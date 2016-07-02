package dotty.tools.dotc
package transform

import core._
import TreeTransforms._
import Contexts.Context
import Flags._
import SymUtils._
import Symbols._
import SymDenotations._
import Types._
import Decorators._
import DenotTransformers._
import StdNames._
import NameOps._
import Phases._
import ast.untpd
import ast.Trees._
import collection.mutable

/** This phase performs the following transformations:
 *
 *   1. (done in `traitDefs` and `transformSym`) Map every concrete trait getter
 *
 *       <mods> def x(): T = expr
 *
 *   to the pair of definitions:
 *
 *       <mods> def x(): T
 *       protected def initial$x(): T = { stats; expr }
 *
 *   where `stats` comprises all statements between either the start of the trait
 *   or the previous field definition which are not definitions (i.e. are executed for
 *   their side effects).
 *
 *   2. (done in `traitDefs`) Make every concrete trait setter
 *
 *      <mods> def x_=(y: T) = ()
 *
 *     deferred by mapping it to
 *
 *      <mods> def x_=(y: T)
 *
 *   3. For a non-trait class C:
 *
 *        For every trait M directly implemented by the class (see SymUtils.mixin), in
 *        reverse linearization order, add the following definitions to C:
 *
 *          3.1 (done in `traitInits`) For every parameter accessor `<mods> def x(): T` in M,
 *              in order of textual occurrence, add
 *
 *               <mods> def x() = e
 *
 *              where `e` is the constructor argument in C that corresponds to `x`. Issue
 *              an error if no such argument exists.
 *
 *          3.2 (done in `traitInits`) For every concrete trait getter `<mods> def x(): T` in M
 *              which is not a parameter accessor, in order of textual occurrence, produce the following:
 *
 *              3.2.1 If `x` is also a member of `C`, and M is a Dotty trait:
 *
 *                <mods> def x(): T = super[M].initial$x()
 *
 *              3.2.2 If `x` is also a member of `C`, and M is a Scala 2.x trait:
 *
 *                <mods> def x(): T = _
 *
 *              3.2.3 If `x` is not a member of `C`, and M is a Dotty trait:
 *
 *                super[M].initial$x()
 *
 *              3.2.4 If `x` is not a member of `C`, and M is a Scala2.x trait, nothing gets added.
 *
 *
 *          3.3 (done in `superCallOpt`) The call:
 *
 *                super[M].<init>
 *
 *          3.4 (done in `setters`) For every concrete setter `<mods> def x_=(y: T)` in M:
 *
 *                <mods> def x_=(y: T) = ()
 *
 *   4. (done in `transformTemplate` and `transformSym`) Drop all parameters from trait
 *      constructors.
 *
 *   5. (done in `transformSym`) Drop ParamAccessor flag from all parameter accessors in traits.
 *
 *  Conceptually, this is the second half of the previous mixin phase. It needs to run
 *  after erasure because it copies references to possibly private inner classes and objects
 *  into enclosing classes where they are not visible. This can only be done if all references
 *  are symbolic.
 */
class Mixin extends MiniPhaseTransform with SymTransformer { thisTransform =>
  import ast.tpd._

  override def phaseName: String = "mixin"

  override def runsAfter: Set[Class[_ <: Phase]] = Set(classOf[Erasure])

  override def transformSym(sym: SymDenotation)(implicit ctx: Context): SymDenotation =
    if (sym.is(Accessor, butNot = Deferred) && sym.owner.is(Trait)) {
      val sym1 =
        if (sym is Lazy) sym
        else sym.copySymDenotation(initFlags = sym.flags &~ ParamAccessor | Deferred)
      sym1.ensureNotPrivate
    }
    else if (sym.isConstructor && sym.owner.is(Trait))
      sym.copySymDenotation(
        name = nme.TRAIT_CONSTRUCTOR,
        info = MethodType(Nil, sym.info.resultType))
    else
      sym

  private def initializer(sym: Symbol)(implicit ctx: Context): TermSymbol = {
    if (sym is Lazy) sym
    else {
      val initName = InitializerName(sym.name.asTermName)
      sym.owner.info.decl(initName).symbol
        .orElse(
          ctx.newSymbol(
            sym.owner,
            initName,
            Protected | Synthetic | Method,
            sym.info,
            coord = sym.symbol.coord).enteredAfter(thisTransform))
    }
  }.asTerm

  override def transformTemplate(impl: Template)(implicit ctx: Context, info: TransformerInfo) = {
    val cls = impl.symbol.owner.asClass
    val ops = new MixinOps(cls, thisTransform)
    import ops._

    def traitDefs(stats: List[Tree]): List[Tree] = {
      val initBuf = new mutable.ListBuffer[Tree]
      stats.flatMap({
        case stat: DefDef if stat.symbol.isGetter && !stat.rhs.isEmpty && !stat.symbol.is(Flags.Lazy) =>
          // make initializer that has all effects of previous getter,
          // replace getter rhs with empty tree.
          val vsym = stat.symbol
          val isym = initializer(vsym)
          val rhs = Block(
            initBuf.toList.map(_.changeOwnerAfter(impl.symbol, isym, thisTransform)),
            stat.rhs.changeOwnerAfter(vsym, isym, thisTransform).wildcardToDefault)
          initBuf.clear()
          cpy.DefDef(stat)(rhs = EmptyTree) :: DefDef(isym, rhs) :: Nil
        case stat: DefDef if stat.symbol.isSetter =>
          cpy.DefDef(stat)(rhs = EmptyTree) :: Nil
        case stat: DefTree =>
          stat :: Nil
        case stat =>
          initBuf += stat
          Nil
      }) ++ initBuf
    }

    /** Map constructor call to a pair of a supercall and a list of arguments
     *  to be used as initializers of trait parameters if the target of the call
     *  is a trait.
     */
    def transformConstructor(tree: Tree): (Tree, List[Tree]) = {
      val Apply(sel @ Select(New(_), nme.CONSTRUCTOR), args) = tree
      val (callArgs, initArgs) = if (tree.symbol.owner.is(Trait)) (Nil, args) else (args, Nil)
      (superRef(tree.symbol, tree.pos).appliedToArgs(callArgs), initArgs)
    }

    val superCallsAndArgs = (
      for (p <- impl.parents if p.symbol.isConstructor)
      yield p.symbol.owner -> transformConstructor(p)
    ).toMap
    val superCalls = superCallsAndArgs.mapValues(_._1)
    val initArgs = superCallsAndArgs.mapValues(_._2)

    def superCallOpt(baseCls: Symbol): List[Tree] = superCalls.get(baseCls) match {
      case Some(call) =>
        if (defn.PhantomClasses.contains(baseCls)) Nil else call :: Nil
      case None =>
        if (baseCls.is(NoInitsTrait) || defn.PhantomClasses.contains(baseCls)) Nil
        else {
          //println(i"synth super call ${baseCls.primaryConstructor}: ${baseCls.primaryConstructor.info}")
          transformFollowingDeep(superRef(baseCls.primaryConstructor).appliedToNone) :: Nil
        }
    }

    def wasDeferred(sym: Symbol) =
      ctx.atPhase(thisTransform) { implicit ctx => sym is Deferred }

    def traitInits(mixin: ClassSymbol): List[Tree] = {
      var argNum = 0
      def nextArgument() = initArgs.get(mixin) match {
        case Some(arguments) =>
          try arguments(argNum) finally argNum += 1
        case None =>
          val (msg, pos) = impl.parents.find(_.tpe.typeSymbol == mixin) match {
            case Some(parent) => ("lacks argument list", parent.pos)
            case None =>
              ("""is indirectly implemented,
                 |needs to be implemented directly so that arguments can be passed""".stripMargin,
               cls.pos)
          }
          ctx.error(i"parameterized $mixin $msg", pos)
          EmptyTree
      }

      for (getter <- mixin.info.decls.filter(getr => getr.isGetter && !wasDeferred(getr)).toList) yield {
        val isScala2x = mixin.is(Scala2x)
        def default = Underscore(getter.info.resultType)
        def initial = transformFollowing(superRef(initializer(getter)).appliedToNone)

        /** A call to the implementation of `getter` in `mixin`'s implementation class */
        def lazyGetterCall = {
          def canbeImplClassGetter(sym: Symbol) = sym.info.firstParamTypes match {
            case t :: Nil => t.isDirectRef(mixin)
            case _ => false
          }
          val implClassGetter = mixin.implClass.info.nonPrivateDecl(getter.name)
            .suchThat(canbeImplClassGetter).symbol
          ref(mixin.implClass).select(implClassGetter).appliedTo(This(cls))
        }

        if (isCurrent(getter) || getter.is(ExpandedName)) {
          val rhs =
            if (ctx.atPhase(thisTransform)(implicit ctx => getter.is(ParamAccessor))) nextArgument()
            else if (isScala2x)
              if (getter.is(Lazy, butNot = Module)) lazyGetterCall
              else if (getter.is(Module))
                New(getter.info.resultType, List(This(cls)))
              else Underscore(getter.info.resultType)
            else transformFollowing(superRef(initializer(getter)).appliedToNone)
          // transformFollowing call is needed to make memoize & lazy vals run
          transformFollowing(DefDef(implementation(getter.asTerm), rhs))
        }
        else if (isScala2x) EmptyTree
        else initial
      }
    }

    def setters(mixin: ClassSymbol): List[Tree] =
      for (setter <- mixin.info.decls.filter(setr => setr.isSetter && !wasDeferred(setr)).toList)
        yield transformFollowing(DefDef(implementation(setter.asTerm), unitLiteral.withPos(cls.pos)))

    cpy.Template(impl)(
      constr =
        if (cls.is(Trait)) cpy.DefDef(impl.constr)(vparamss = Nil :: Nil)
        else impl.constr,
      parents = impl.parents.map(p => TypeTree(p.tpe).withPos(p.pos)),
      body =
        if (cls is Trait) traitDefs(impl.body)
        else {
          val mixInits = mixins.flatMap { mixin =>
            flatten(traitInits(mixin)) ::: superCallOpt(mixin) ::: setters(mixin)
          }
          superCallOpt(superCls) ::: mixInits ::: impl.body
        })
  }
}
