package dotty.tools.dotc
package transform

import core.Phases._
import core.DenotTransformers._
import core.Denotations._
import core.SymDenotations._
import core.Symbols._
import core.Contexts._
import core.Types._
import core.Names._
import core.StdNames._
import core.NameOps._
import core.Decorators._
import core.Constants._
import typer.NoChecking
import typer.ProtoTypes._
import typer.ErrorReporting._
import core.TypeErasure._
import core.Decorators._
import dotty.tools.dotc.ast.{Trees, tpd, untpd}
import ast.Trees._
import scala.collection.mutable.ListBuffer
import dotty.tools.dotc.core.{Constants, Flags}
import ValueClasses._
import TypeUtils._
import ExplicitOuter._
import core.Mode

class Erasure extends Phase with DenotTransformer { thisTransformer =>

  override def phaseName: String = "erasure"

  /** List of names of phases that should precede this phase */
  override def runsAfter: Set[Class[_ <: Phase]] = Set(classOf[InterceptedMethods], classOf[Splitter], classOf[ElimRepeated])

  def transform(ref: SingleDenotation)(implicit ctx: Context): SingleDenotation = ref match {
    case ref: SymDenotation =>
      assert(ctx.phase == this, s"transforming $ref at ${ctx.phase}")
      if (ref.symbol eq defn.ObjectClass) {
        // Aftre erasure, all former Any members are now Object members
        val ClassInfo(pre, _, ps, decls, selfInfo) = ref.info
        val extendedScope = decls.cloneScope
        for (decl <- defn.AnyClass.classInfo.decls)
          if (!decl.isConstructor) extendedScope.enter(decl)
        ref.copySymDenotation(
          info = transformInfo(ref.symbol,
              ClassInfo(pre, defn.ObjectClass, ps, extendedScope, selfInfo))
        )
      }
      else {
        val oldSymbol = ref.symbol
        val newSymbol =
          if ((oldSymbol.owner eq defn.AnyClass) && oldSymbol.isConstructor)
            defn.ObjectClass.primaryConstructor
        else oldSymbol
        val oldOwner = ref.owner
        val newOwner = if (oldOwner eq defn.AnyClass) defn.ObjectClass else oldOwner
        val oldInfo = ref.info
        val newInfo = transformInfo(ref.symbol, oldInfo)
        val oldFlags = ref.flags
        val newFlags = ref.flags &~ Flags.HasDefaultParams // HasDefaultParams needs to be dropped because overriding might become overloading
        // TODO: define derivedSymDenotation?
        if ((oldSymbol eq newSymbol) && (oldOwner eq newOwner) && (oldInfo eq newInfo) && (oldFlags == newFlags)) ref
        else {
          assert(!ref.is(Flags.PackageClass), s"trans $ref @ ${ctx.phase} oldOwner = $oldOwner, newOwner = $newOwner, oldInfo = $oldInfo, newInfo = $newInfo ${oldOwner eq newOwner} ${oldInfo eq newInfo}")
          ref.copySymDenotation(symbol = newSymbol, owner = newOwner, initFlags = newFlags, info = newInfo)
        }
      }
    case ref =>
      ref.derivedSingleDenotation(ref.symbol, transformInfo(ref.symbol, ref.info))
  }

  val eraser = new Erasure.Typer

  def run(implicit ctx: Context): Unit = {
    val unit = ctx.compilationUnit
    unit.tpdTree = eraser.typedExpr(unit.tpdTree)(ctx.fresh.setPhase(this.next))
  }

  override def checkPostCondition(tree: tpd.Tree)(implicit ctx: Context) = {
    assertErased(tree)
    tree match {
      case res: tpd.This =>
        assert(!ExplicitOuter.referencesOuter(ctx.owner.enclosingClass, res),
          i"Reference to $res from ${ctx.owner.showLocated}")
      case ret: tpd.Return =>
        // checked only after erasure, as checking before erasure is complicated
        // due presence of type params in returned types
        val from = if (ret.from.isEmpty) ctx.owner.enclosingMethod else ret.from.symbol
        val rType = from.info.finalResultType
        assert(ret.expr.tpe <:< rType,
          i"Returned value:${ret.expr}  does not conform to result type(${ret.expr.tpe.widen} of method $from")
      case _ =>
    }
  }

  /** Assert that tree type and its widened underlying type are erased.
   *  Also assert that term refs have fixed symbols (so we are sure
   *  they need not be reloaded using member; this would likely fail as signatures
   *  may change after erasure).
   */
  def assertErased(tree: tpd.Tree)(implicit ctx: Context): Unit = {
    assertErased(tree.typeOpt, tree)
    if (!defn.isPolymorphicAfterErasure(tree.symbol))
      assertErased(tree.typeOpt.widen, tree)
    if (ctx.mode.isExpr)
      tree.tpe match {
        case ref: TermRef =>
          assert(ref.denot.isInstanceOf[SymDenotation] ||
              ref.denot.isInstanceOf[UniqueRefDenotation],
            i"non-sym type $ref of class ${ref.getClass} with denot of class ${ref.denot.getClass} of $tree")
        case _ =>
      }
  }

  def assertErased(tp: Type, tree: tpd.Tree = tpd.EmptyTree)(implicit ctx: Context): Unit =
    if (tp.typeSymbol == defn.ArrayClass &&
        ctx.compilationUnit.source.file.name == "Array.scala") {} // ok
    else
      assert(isErasedType(tp),
        i"The type $tp - ${tp.toString} of class ${tp.getClass} of tree $tree : ${tree.tpe} / ${tree.getClass} is illegal after erasure, phase = ${ctx.phase.prev}")
}

object Erasure extends TypeTestsCasts{

  import tpd._

  object Boxing {

    def isUnbox(sym: Symbol)(implicit ctx: Context) =
      sym.name == nme.unbox && sym.owner.linkedClass.isPrimitiveValueClass

    def isBox(sym: Symbol)(implicit ctx: Context) =
      sym.name == nme.box && sym.owner.linkedClass.isPrimitiveValueClass

    def boxMethod(cls: ClassSymbol)(implicit ctx: Context) =
      cls.linkedClass.info.member(nme.box).symbol
    def unboxMethod(cls: ClassSymbol)(implicit ctx: Context) =
      cls.linkedClass.info.member(nme.unbox).symbol

    /** Isf this tree is an unbox operation which can be safely removed
     *  when enclosed in a box, the unboxed argument, otherwise EmptyTree.
     *  Note that one can't always remove a Box(Unbox(x)) combination because the
     *  process of unboxing x may lead to throwing an exception.
     *  This is important for specialization: calls to the super constructor should not box/unbox specialized
     *  fields (see TupleX). (ID)
     */
    private def safelyRemovableUnboxArg(tree: Tree)(implicit ctx: Context): Tree = tree match {
      case Apply(fn, arg :: Nil)
      if isUnbox(fn.symbol) && defn.ScalaBoxedClasses().contains(arg.tpe.widen.typeSymbol) =>
        arg
      case _ =>
        EmptyTree
    }

    def constant(tree: Tree, const: Tree)(implicit ctx: Context) =
      if (isPureExpr(tree)) const else Block(tree :: Nil, const)

    final def box(tree: Tree, target: => String = "")(implicit ctx: Context): Tree = ctx.traceIndented(i"boxing ${tree.showSummary}: ${tree.tpe} into $target") {
      tree.tpe.widen match {
        case ErasedValueType(tycon, _) =>
          New(tycon, cast(tree, underlyingOfValueClass(tycon.symbol.asClass)) :: Nil) // todo: use adaptToType?
        case tp =>
          val cls = tp.classSymbol
          if (cls eq defn.UnitClass) constant(tree, ref(defn.BoxedUnit_UNIT))
          else if (cls eq defn.NothingClass) tree // a non-terminating expression doesn't need boxing
          else {
            assert(cls ne defn.ArrayClass)
            val arg = safelyRemovableUnboxArg(tree)
            if (arg.isEmpty) ref(boxMethod(cls.asClass)).appliedTo(tree)
            else {
              ctx.log(s"boxing an unbox: ${tree.symbol} -> ${arg.tpe}")
              arg
            }
          }
      }
    }

    def unbox(tree: Tree, pt: Type)(implicit ctx: Context): Tree = ctx.traceIndented(i"unboxing ${tree.showSummary}: ${tree.tpe} as a $pt") {
      pt match {
        case ErasedValueType(tycon, underlying) =>
          def unboxedTree(t: Tree) =
            adaptToType(t, tycon)
            .select(valueClassUnbox(tycon.symbol.asClass))
            .appliedToNone

          // Null unboxing needs to be treated separately since we cannot call a method on null.
          // "Unboxing" null to underlying is equivalent to doing null.asInstanceOf[underlying]
          // See tests/pos/valueclasses/nullAsInstanceOfVC.scala for cases where this might happen.
          val tree1 =
            if (tree.tpe isRef defn.NullClass)
              adaptToType(tree, underlying)
            else if (!(tree.tpe <:< tycon)) {
              assert(!(tree.tpe.typeSymbol.isPrimitiveValueClass))
              val nullTree = Literal(Constant(null))
              val unboxedNull = adaptToType(nullTree, underlying)

              evalOnce(tree) { t =>
                If(t.select(defn.Object_eq).appliedTo(nullTree),
                  unboxedNull,
                  unboxedTree(t))
              }
            } else unboxedTree(tree)

          cast(tree1, pt)
        case _ =>
          val cls = pt.widen.classSymbol
          if (cls eq defn.UnitClass) constant(tree, Literal(Constant(())))
          else {
            assert(cls ne defn.ArrayClass)
            ref(unboxMethod(cls.asClass)).appliedTo(tree)
          }
      }
    }

    /** Generate a synthetic cast operation from tree.tpe to pt.
     *  Does not do any boxing/unboxing (this is handled upstream).
     *  Casts from and to ErasedValueType are special, see the explanation
     *  in ExtensionMethods#transform.
     */
    def cast(tree: Tree, pt: Type)(implicit ctx: Context): Tree = {
      // TODO: The commented out assertion fails for tailcall/t6574.scala
      //       Fix the problem and enable the assertion.
      // assert(!pt.isInstanceOf[SingletonType], pt)
      if (pt isRef defn.UnitClass) unbox(tree, pt)
      else (tree.tpe, pt) match {
        case (JavaArrayType(treeElem), JavaArrayType(ptElem))
        if treeElem.widen.isPrimitiveValueType && !ptElem.isPrimitiveValueType =>
          // See SI-2386 for one example of when this might be necessary.
          cast(ref(defn.runtimeMethodRef(nme.toObjectArray)).appliedTo(tree), pt)
        case (_, ErasedValueType(tycon, _)) =>
          ref(u2evt(tycon.symbol.asClass)).appliedTo(tree)
        case _ =>
          tree.tpe.widen match {
            case ErasedValueType(tycon, _) =>
              ref(evt2u(tycon.symbol.asClass)).appliedTo(tree)
            case _ =>
              if (pt.isPrimitiveValueType)
                primitiveConversion(tree, pt.classSymbol)
              else
                tree.asInstance(pt)
          }
      }
    }

    /** Adaptation of an expression `e` to an expected type `PT`, applying the following
     *  rewritings exhaustively as long as the type of `e` is not a subtype of `PT`.
     *
     *    e -> e()           if `e` appears not as the function part of an application
     *    e -> box(e)        if `e` is of erased value type
     *    e -> unbox(e, PT)  otherwise, if `PT` is an erased value type
     *    e -> box(e)        if `e` is of primitive type and `PT` is not a primitive type
     *    e -> unbox(e, PT)  if `PT` is a primitive type and `e` is not of primitive type
     *    e -> cast(e, PT)   otherwise
     */
    def adaptToType(tree: Tree, pt: Type)(implicit ctx: Context): Tree =
      if (pt.isInstanceOf[FunProto]) tree
      else tree.tpe.widen match {
        case MethodType(Nil, _) if tree.isTerm =>
          adaptToType(tree.appliedToNone, pt)
        case tpw =>
          if (pt.isInstanceOf[ProtoType] || tree.tpe <:< pt)
            tree
          else if (tpw.isErasedValueType)
            adaptToType(box(tree), pt)
          else if (pt.isErasedValueType)
            adaptToType(unbox(tree, pt), pt)
          else if (tpw.isPrimitiveValueType && !pt.isPrimitiveValueType)
            adaptToType(box(tree), pt)
          else if (pt.isPrimitiveValueType && !tpw.isPrimitiveValueType)
            adaptToType(unbox(tree, pt), pt)
          else
            cast(tree, pt)
      }
  }

  class Typer extends typer.ReTyper with NoChecking {
    import Boxing._

    def erasedType(tree: untpd.Tree)(implicit ctx: Context): Type = {
      val tp = tree.typeOpt
      if (tree.isTerm) erasedRef(tp) else valueErasure(tp)
    }

    override def promote(tree: untpd.Tree)(implicit ctx: Context): tree.ThisTree[Type] = {
      assert(tree.hasType)
      val erased = erasedType(tree)
      ctx.log(s"promoting ${tree.show}: ${erased.showWithUnderlying()}")
      tree.withType(erased)
    }

    /** When erasing most TypeTrees we should not semi-erase value types.
     *  This is not the case for [[DefDef#tpt]], [[ValDef#tpt]] and [[Typed#tpt]], they
     *  are handled separately by [[typedDefDef]], [[typedValDef]] and [[typedTyped]].
     */
    override def typedTypeTree(tree: untpd.TypeTree, pt: Type)(implicit ctx: Context): TypeTree =
      tree.withType(erasure(tree.tpe))

    /** This override is only needed to semi-erase type ascriptions */
    override def typedTyped(tree: untpd.Typed, pt: Type)(implicit ctx: Context): Tree = {
      val Typed(expr, tpt) = tree
      val tpt1 = promote(tpt)
      val expr1 = typed(expr, tpt1.tpe)
      assignType(untpd.cpy.Typed(tree)(expr1, tpt1), tpt1)
    }

    override def typedLiteral(tree: untpd.Literal)(implicit ctx: Context): Literal =
      if (tree.typeOpt.isRef(defn.UnitClass)) tree.withType(tree.typeOpt)
      else if (tree.const.tag == Constants.ClazzTag) Literal(Constant(erasure(tree.const.typeValue)))
      else super.typedLiteral(tree)

    /** Type check select nodes, applying the following rewritings exhaustively
     *  on selections `e.m`, where `OT` is the type of the owner of `m` and `ET`
     *  is the erased type of the selection's original qualifier expression.
     *
     *      e.m1 -> e.m2          if `m1` is a member of Any or AnyVal and `m2` is
     *                            the same-named member in Object.
     *      e.m -> box(e).m       if `e` is primitive and `m` is a member or a reference class
     *                            or `e` has an erased value class type.
     *      e.m -> unbox(e).m     if `e` is not primitive and `m` is a member of a primtive type.
     *      e.m -> cast(e, OT).m  if the type of `e` does not conform to OT and `m`
     *                            is not an array operation.
     *
     *  If `m` is an array operation, i.e. one of the members apply, update, length, clone, and
     *  <init> of class Array, we additionally try the following rewritings:
     *
     *      e.m -> runtime.array_m(e)   if ET is Object
     *      e.m -> cast(e, ET).m        if the type of `e` does not conform to ET
     *      e.clone -> e.clone'         where clone' is Object's clone method
     *      e.m -> e.[]m                if `m` is an array operation other than `clone`.
     */
    override def typedSelect(tree: untpd.Select, pt: Type)(implicit ctx: Context): Tree = {
      val sym = tree.symbol
      assert(sym.exists, tree.show)

      def select(qual: Tree, sym: Symbol): Tree = {
        val name = tree.typeOpt match {
          case tp: NamedType if tp.name.isShadowedName => sym.name.shadowedName
          case _ => sym.name
        }
        untpd.cpy.Select(tree)(qual, sym.name)
          .withType(NamedType.withFixedSym(qual.tpe, sym))
      }

      def selectArrayMember(qual: Tree, erasedPre: Type): Tree =
        if (erasedPre isRef defn.ObjectClass)
          runtimeCallWithProtoArgs(tree.name.genericArrayOp, pt, qual)
        else if (!(qual.tpe <:< erasedPre))
          selectArrayMember(cast(qual, erasedPre), erasedPre)
        else
          assignType(untpd.cpy.Select(tree)(qual, tree.name.primitiveArrayOp), qual)

      def adaptIfSuper(qual: Tree): Tree = qual match {
        case Super(thisQual, tpnme.EMPTY) =>
          val SuperType(thisType, supType) = qual.tpe
          if (sym.owner is Flags.Trait)
            cpy.Super(qual)(thisQual, sym.owner.asClass.name)
              .withType(SuperType(thisType, sym.owner.typeRef))
          else
            qual.withType(SuperType(thisType, thisType.firstParent))
        case _ =>
          qual
      }

      def recur(qual: Tree): Tree = {
        val qualIsPrimitive = qual.tpe.widen.isPrimitiveValueType
        val symIsPrimitive = sym.owner.isPrimitiveValueClass
        if ((sym.owner eq defn.AnyClass) || (sym.owner eq defn.AnyValClass)) {
          assert(sym.isConstructor, s"${sym.showLocated}")
          select(qual, defn.ObjectClass.info.decl(sym.name).symbol)
        }
        else if (qualIsPrimitive && !symIsPrimitive || qual.tpe.widenDealias.isErasedValueType)
          recur(box(qual))
        else if (!qualIsPrimitive && symIsPrimitive)
          recur(unbox(qual, sym.owner.typeRef))
        else if (sym.owner eq defn.ArrayClass)
          selectArrayMember(qual, erasure(tree.qualifier.typeOpt.widen.finalResultType))
        else {
          val qual1 = adaptIfSuper(qual)
          if (qual1.tpe.derivesFrom(sym.owner) || qual1.isInstanceOf[Super])
            select(qual1, sym)
          else
            recur(cast(qual1, sym.owner.typeRef))
        }
      }

      recur(typed(tree.qualifier, AnySelectionProto))
    }

    override def typedSelectFromTypeTree(tree: untpd.SelectFromTypeTree, pt: Type)(implicit ctx: Context) =
      untpd.Ident(tree.name).withPos(tree.pos).withType(erasedType(tree))

    override def typedThis(tree: untpd.This)(implicit ctx: Context): Tree =
      if (tree.symbol == ctx.owner.enclosingClass || tree.symbol.isStaticOwner) promote(tree)
      else {
        ctx.log(i"computing outer path from ${ctx.owner.ownersIterator.toList}%, % to ${tree.symbol}, encl class = ${ctx.owner.enclosingClass}")
        outer.path(tree.symbol)
      }

    private def runtimeCallWithProtoArgs(name: Name, pt: Type, args: Tree*)(implicit ctx: Context): Tree = {
      val meth = defn.runtimeMethodRef(name)
      val followingParams = meth.symbol.info.firstParamTypes.drop(args.length)
      val followingArgs = protoArgs(pt).zipWithConserve(followingParams)(typedExpr).asInstanceOf[List[tpd.Tree]]
      ref(meth).appliedToArgs(args.toList ++ followingArgs)
    }

    private def protoArgs(pt: Type): List[untpd.Tree] = pt match {
      case pt: FunProto => pt.args ++ protoArgs(pt.resType)
      case _ => Nil
    }

    override def typedTypeApply(tree: untpd.TypeApply, pt: Type)(implicit ctx: Context) = {
      val ntree = interceptTypeApply(tree.asInstanceOf[TypeApply])(ctx.withPhase(ctx.erasurePhase))

      ntree match {
        case TypeApply(fun, args) =>
          val fun1 = typedExpr(fun, WildcardType)
          fun1.tpe.widen match {
            case funTpe: PolyType =>
              val args1 = args.mapconserve(typedType(_))
              untpd.cpy.TypeApply(tree)(fun1, args1).withType(funTpe.instantiate(args1.tpes))
            case _ => fun1
          }
        case _ => typedExpr(ntree, pt)
      }
    }

    override def typedApply(tree: untpd.Apply, pt: Type)(implicit ctx: Context): Tree = {
      val Apply(fun, args) = tree
      if (fun.symbol == defn.dummyApply)
        typedUnadapted(args.head, pt)
      else typedExpr(fun, FunProto(args, pt, this)) match {
        case fun1: Apply => // arguments passed in prototype were already passed
          fun1
        case fun1 =>
          fun1.tpe.widen match {
            case mt: MethodType =>
              val outers = outer.args(fun.asInstanceOf[tpd.Tree]) // can't use fun1 here because its type is already erased
              val args1 = (outers ::: args ++ protoArgs(pt)).zipWithConserve(mt.paramTypes)(typedExpr)
              untpd.cpy.Apply(tree)(fun1, args1) withType mt.resultType
            case _ =>
              throw new MatchError(i"tree $tree has unexpected type of function ${fun1.tpe.widen}, was ${fun.typeOpt.widen}")
          }
      }
    }

    // The following four methods take as the proto-type the erasure of the pre-existing type,
    // if the original proto-type is not a value type.
    // This makes all branches be adapted to the correct type.
    override def typedSeqLiteral(tree: untpd.SeqLiteral, pt: Type)(implicit ctx: Context) =
      super.typedSeqLiteral(tree, erasure(tree.typeOpt))
        // proto type of typed seq literal is original type;

    override def typedIf(tree: untpd.If, pt: Type)(implicit ctx: Context) =
      super.typedIf(tree, adaptProto(tree, pt))

    override def typedMatch(tree: untpd.Match, pt: Type)(implicit ctx: Context) =
      super.typedMatch(tree, adaptProto(tree, pt))

    override def typedTry(tree: untpd.Try, pt: Type)(implicit ctx: Context) =
      super.typedTry(tree, adaptProto(tree, pt))

    private def adaptProto(tree: untpd.Tree, pt: Type)(implicit ctx: Context) = {
      if (pt.isValueType) pt else {
        if (tree.typeOpt.derivesFrom(ctx.definitions.UnitClass))
          tree.typeOpt
        else valueErasure(tree.typeOpt)
      }
    }

    override def typedValDef(vdef: untpd.ValDef, sym: Symbol)(implicit ctx: Context): ValDef =
      super.typedValDef(untpd.cpy.ValDef(vdef)(
        tpt = untpd.TypedSplice(TypeTree(sym.info).withPos(vdef.tpt.pos))), sym)

    override def typedDefDef(ddef: untpd.DefDef, sym: Symbol)(implicit ctx: Context) = {
      val restpe =
        if (sym.isConstructor) defn.UnitType
        else sym.info.resultType
      val ddef1 = untpd.cpy.DefDef(ddef)(
        tparams = Nil,
        vparamss = (outer.paramDefs(sym) ::: ddef.vparamss.flatten) :: Nil,
        tpt = untpd.TypedSplice(TypeTree(restpe).withPos(ddef.tpt.pos)),
        rhs = ddef.rhs match {
          case id @ Ident(nme.WILDCARD) => untpd.TypedSplice(id.withType(restpe))
          case _ => ddef.rhs
        })
      super.typedDefDef(ddef1, sym)
    }

    /** After erasure, we may have to replace the closure method by a bridge.
     *  LambdaMetaFactory handles this automatically for most types, but we have
     *  to deal with boxing and unboxing of value classes ourselves.
     */
    override def typedClosure(tree: untpd.Closure, pt: Type)(implicit ctx: Context) = {
      val implClosure @ Closure(_, meth, _) = super.typedClosure(tree, pt)
      implClosure.tpe match {
        case SAMType(sam) =>
          val implType = meth.tpe.widen

          val List(implParamTypes) = implType.paramTypess
          val List(samParamTypes) = sam.info.paramTypess
          val implResultType = implType.resultType
          val samResultType = sam.info.resultType

          // Given a value class V with an underlying type U, the following code:
          //   val f: Function1[V, V] = x => ...
          // results in the creation of a closure and a method:
          //   def $anonfun(v1: V): V = ...
          //   val f: Function1[V, V] = closure($anonfun)
          // After [[Erasure]] this method will look like:
          //   def $anonfun(v1: ErasedValueType(V, U)): ErasedValueType(V, U) = ...
          // And after [[ElimErasedValueType]] it will look like:
          //   def $anonfun(v1: U): U = ...
          // This method does not implement the SAM of Function1[V, V] anymore and
          // needs to be replaced by a bridge:
          //   def $anonfun$2(v1: V): V = new V($anonfun(v1.underlying))
          //   val f: Function1 = closure($anonfun$2)
          // In general, a bridge is needed when the signature of the closure method after
          // Erasure contains an ErasedValueType but the corresponding type in the functional
          // interface is not an ErasedValueType.
          val bridgeNeeded =
            (implResultType :: implParamTypes, samResultType :: samParamTypes).zipped.exists(
              (implType, samType) => implType.isErasedValueType && !samType.isErasedValueType
            )

          if (bridgeNeeded) {
            val bridge = ctx.newSymbol(ctx.owner, nme.ANON_FUN, Flags.Synthetic | Flags.Method, sam.info)
            val bridgeCtx = ctx.withOwner(bridge)
            Closure(bridge, bridgeParamss => {
              implicit val ctx: Context = bridgeCtx

              val List(bridgeParams) = bridgeParamss
              val rhs = Apply(meth, (bridgeParams, implParamTypes).zipped.map(adapt(_, _)))
              adapt(rhs, sam.info.resultType)
            })
          } else implClosure
        case _ =>
          implClosure
      }
    }

    override def typedTypeDef(tdef: untpd.TypeDef, sym: Symbol)(implicit ctx: Context) =
      EmptyTree

    override def typedStats(stats: List[untpd.Tree], exprOwner: Symbol)(implicit ctx: Context): List[Tree] = {
      val stats1 = Trees.flatten(super.typedStats(stats, exprOwner))
      if (ctx.owner.isClass) stats1 ::: addBridges(stats, stats1)(ctx) else stats1
    }

    // this implementation doesn't check for bridge clashes with value types!
    def addBridges(oldStats: List[untpd.Tree], newStats: List[tpd.Tree])(implicit ctx: Context): List[tpd.Tree] = {
      val beforeCtx = ctx.withPhase(ctx.erasurePhase)
      def traverse(after: List[Tree], before: List[untpd.Tree],
                   emittedBridges: ListBuffer[tpd.DefDef] = ListBuffer[tpd.DefDef]()): List[tpd.DefDef] = {
        after match {
          case Nil => emittedBridges.toList
          case (member: DefDef) :: newTail =>
            before match {
              case Nil => emittedBridges.toList
              case (oldMember: untpd.DefDef) :: oldTail =>
                try {
                  val oldSymbol = oldMember.symbol(beforeCtx)
                  val newSymbol = member.symbol(ctx)
                  assert(oldSymbol.name(beforeCtx) == newSymbol.name,
                    s"${oldSymbol.name(beforeCtx)} bridging with ${newSymbol.name}")
                  val newOverridden = oldSymbol.denot.allOverriddenSymbols.toSet // TODO: clarify new <-> old in a comment; symbols are swapped here
                  val oldOverridden = newSymbol.allOverriddenSymbols(beforeCtx).toSet // TODO: can we find a more efficient impl? newOverridden does not have to be a set!
                  def stillInBaseClass(sym: Symbol) = ctx.owner derivesFrom sym.owner
                  val neededBridges = (oldOverridden -- newOverridden).filter(stillInBaseClass)

                  var minimalSet = Set[Symbol]()
                  // compute minimal set of bridges that are needed:
                  for (bridge <- neededBridges) {
                    val isRequired = minimalSet.forall(nxtBridge => !(bridge.info =:= nxtBridge.info))

                    if (isRequired) {
                      // check for clashes
                      val clash: Option[Symbol] = oldSymbol.owner.info.decls.lookupAll(bridge.name).find {
                        sym =>
                          (sym.name eq bridge.name) && sym.info.widen =:= bridge.info.widen
                      }.orElse(
                        emittedBridges.find(stat => (stat.name == bridge.name) && stat.tpe.widen =:= bridge.info.widen)
                          .map(_.symbol))
                      clash match {
                        case Some(cl) =>
                          ctx.error(i"bridge for method ${newSymbol.showLocated(beforeCtx)} of type ${newSymbol.info(beforeCtx)}\n" +
                            i"clashes with ${cl.symbol.showLocated(beforeCtx)} of type ${cl.symbol.info(beforeCtx)}\n" +
                            i"both have same type after erasure: ${bridge.symbol.info}")
                        case None => minimalSet += bridge
                      }
                    }
                  }

                  val bridgeImplementations = minimalSet.map {
                    sym => makeBridgeDef(member, sym)(ctx)
                  }
                  emittedBridges ++= bridgeImplementations
                } catch {
                  case ex: MergeError => ctx.error(ex.getMessage, member.pos)
                }

                traverse(newTail, oldTail, emittedBridges)
              case notADefDef :: oldTail =>
                traverse(after, oldTail, emittedBridges)
            }
          case notADefDef :: newTail =>
            traverse(newTail, before, emittedBridges)
        }
      }

      traverse(newStats, oldStats)
    }

    private final val NoBridgeFlags = Flags.Accessor | Flags.Deferred | Flags.Lazy | Flags.ParamAccessor

    /** Create a bridge DefDef which overrides a parent method.
     *
     *  @param newDef     The DefDef which needs bridging because its signature
     *                    does not match the parent method signature
     *  @param parentSym  A symbol corresponding to the parent method to override
     *  @return           A new DefDef whose signature matches the parent method
     *                    and whose body only contains a call to newDef
     */
    def makeBridgeDef(newDef: tpd.DefDef, parentSym: Symbol)(implicit ctx: Context): tpd.DefDef = {
      val newDefSym = newDef.symbol
      val currentClass = newDefSym.owner.asClass

      def error(reason: String) = {
        assert(false, s"failure creating bridge from ${newDefSym} to ${parentSym}, reason: $reason")
        ???
      }
      var excluded = NoBridgeFlags
      if (!newDefSym.is(Flags.Protected)) excluded |= Flags.Protected // needed to avoid "weaker access" assertion failures in expandPrivate
      val bridge = ctx.newSymbol(currentClass,
        parentSym.name, parentSym.flags &~ excluded | Flags.Bridge, parentSym.info, coord = newDefSym.owner.coord).asTerm
      bridge.enteredAfter(ctx.phase.prev.asInstanceOf[DenotTransformer]) // this should be safe, as we're executing in context of next phase
      ctx.debuglog(s"generating bridge from ${newDefSym} to $bridge")

      val sel: Tree = This(currentClass).select(newDefSym.termRef)

      val resultType = parentSym.info.widen.resultType

      val bridgeCtx = ctx.withOwner(bridge)

      tpd.DefDef(bridge, { paramss: List[List[tpd.Tree]] =>
          implicit val ctx: Context = bridgeCtx

          val rhs = paramss.foldLeft(sel)((fun, vparams) =>
            fun.tpe.widen match {
              case MethodType(names, types) => Apply(fun, (vparams, types).zipped.map(adapt(_, _, untpd.EmptyTree)))
              case a => error(s"can not resolve apply type $a")

            })
          adapt(rhs, resultType)
      })
    }

    override def adapt(tree: Tree, pt: Type, original: untpd.Tree)(implicit ctx: Context): Tree =
      ctx.traceIndented(i"adapting ${tree.showSummary}: ${tree.tpe} to $pt", show = true) {
        assert(ctx.phase == ctx.erasurePhase.next, ctx.phase)
        if (tree.isEmpty) tree
        else if (ctx.mode is Mode.Pattern) tree // TODO: replace with assertion once pattern matcher is active
        else adaptToType(tree, pt)
      }
  }
}
