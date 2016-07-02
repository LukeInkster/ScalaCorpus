package dotty.tools.dotc
package transform

import TreeTransforms._
import core.DenotTransformers._
import core.Symbols._
import core.Contexts._
import core.Types._
import core.Flags._
import core.Decorators._
import core.StdNames.nme
import core.Names._
import core.NameOps._
import core.Phases._
import ast.Trees._
import SymUtils._
import ExplicitOuter.outer
import util.Attachment
import util.NameTransformer
import util.Positions._
import collection.{ mutable, immutable }
import collection.mutable.{ HashMap, HashSet, LinkedHashMap, LinkedHashSet, TreeSet }

object LambdaLift {
  private val NJ = NameTransformer.NAME_JOIN_STRING
  private class NoPath extends Exception
}

/** This phase performs the necessary rewritings to eliminate classes and methods
 *  nested in other methods. In detail:
 *   1. It adds all free variables of local functions as additional parameters (proxies).
 *   2. It rebinds references to free variables to the corresponding proxies,
 *   3. It lifts all local functions and classes out as far as possible, but at least
 *      to the enclosing class.
 *   4. It stores free variables of non-trait classes as additional fields of the class.
 *      The fields serve as proxies for methods in the class, which avoids the need
 *      of passing additional parameters to these methods.
 *
 *  A particularly tricky case are local traits. These cannot store free variables
 *  as field proxies, because LambdaLift runs after Mixin, so the fields cannot be
 *  expanded anymore. Instead, methods of local traits get free variables of
 *  the trait as additional proxy parameters. The difference between local classes
 *  and local traits is illustrated by the two rewritings below.
 *
 *     def f(x: Int) = {           def f(x: Int) = new C(x).f2
 *       class C {          ==>    class C(x$1: Int) {
 *         def f2 = x                def f2 = x$1
 *       }                         }
 *       new C().f2
 *     }
 *
 *     def f(x: Int) = {           def f(x: Int) = new C().f2(x)
 *       trait T {          ==>    trait T
 *         def f2 = x                def f2(x$1: Int) = x$1
 *       }                         }
 *       class C extends T         class C extends T
 *       new C().f2
 *     }
 */
class LambdaLift extends MiniPhase with IdentityDenotTransformer { thisTransform =>
  import LambdaLift._
  import ast.tpd._

  /** the following two members override abstract members in Transform */
  val phaseName: String = "lambdaLift"
  val treeTransform = new LambdaLifter

  override def relaxedTyping = true

  override def runsAfter: Set[Class[_ <: Phase]] = Set(classOf[Constructors])
    // Constructors has to happen before LambdaLift because the lambda lift logic
    // becomes simpler if it can assume that parameter accessors have already been
    // converted to parameters in super calls. Without this it is very hard to get
    // lambda lift for super calls right. Witness the implementation restrictions to
    // this effect in scalac.

  class LambdaLifter extends TreeTransform {
    override def phase = thisTransform

    private type SymSet = TreeSet[Symbol]

    /** A map storing free variables of functions and classes */
    private val free = new LinkedHashMap[Symbol, SymSet]

    /** A map storing the free variable proxies of functions and classes.
     *  For every function and class, this is a map from the free variables
     *  of that function or class to the proxy symbols accessing them.
     */
    private val proxyMap = new LinkedHashMap[Symbol, Map[Symbol, Symbol]]

    /** A hashtable storing calls between functions */
    private val called = new LinkedHashMap[Symbol, SymSet]

    /** Symbols that are called from an inner class. */
    private val calledFromInner = new HashSet[Symbol]

    /** A map from local methods and classes to the owners to which they will be lifted as members.
     *  For methods and classes that do not have any dependencies this will be the enclosing package.
     *  symbols with packages as lifted owners will subsequently represented as static
     *  members of their toplevel class, unless their enclosing class was already static.
     *  Note: During tree transform (which runs at phase LambdaLift + 1), liftedOwner
     *  is also used to decide whether a method had a term owner before.
     */
    private val liftedOwner = new HashMap[Symbol, Symbol]

    /** The outer parameter of a constructor */
    private val outerParam = new HashMap[Symbol, Symbol]

    /** Buffers for lifted out classes and methods, indexed by owner */
    private val liftedDefs = new HashMap[Symbol, mutable.ListBuffer[Tree]]

    /** A flag to indicate whether new free variables have been found */
    private var changedFreeVars: Boolean = _

    /** A flag to indicate whether lifted owners have changed */
    private var changedLiftedOwner: Boolean = _

    private val ord: Ordering[Symbol] = Ordering.by((_: Symbol).id) // Dotty deviation: Type annotation needed. TODO: figure out why
    private def newSymSet = TreeSet.empty[Symbol](ord)

    private def symSet(f: LinkedHashMap[Symbol, SymSet], sym: Symbol): SymSet =
      f.getOrElseUpdate(sym, newSymSet)

    def freeVars(sym: Symbol): List[Symbol] = free.getOrElse(sym, Nil).toList

    def proxyOf(sym: Symbol, fv: Symbol) = proxyMap.getOrElse(sym, Map.empty)(fv)

    def proxies(sym: Symbol): List[Symbol] =  freeVars(sym).map(proxyOf(sym, _))

    /** A symbol is local if it is owned by a term or a local trait,
     *  or if it is a constructor of a local symbol.
     */
    def isLocal(sym: Symbol)(implicit ctx: Context): Boolean = {
      val owner = sym.maybeOwner
      owner.isTerm ||
      owner.is(Trait) && isLocal(owner) ||
      sym.isConstructor && isLocal(owner)
    }

    /** Set `liftedOwner(sym)` to `owner` if `owner` is more deeply nested
     *  than the previous value of `liftedowner(sym)`.
     */
    def narrowLiftedOwner(sym: Symbol, owner: Symbol)(implicit ctx: Context) =
      if (sym.maybeOwner.isTerm &&
        owner.isProperlyContainedIn(liftedOwner(sym)) &&
        owner != sym) {
        ctx.log(i"narrow lifted $sym to $owner")
        changedLiftedOwner = true
        liftedOwner(sym) = owner
      }

    /** Mark symbol `sym` as being free in `enclosure`, unless `sym` is defined
     *  in `enclosure` or there is an intermediate class properly containing `enclosure`
     *  in which `sym` is also free. Also, update `liftedOwner` of `enclosure` so
     *  that `enclosure` can access `sym`, or its proxy in an intermediate class.
     *  This means:
     *
     *    1. If there is an intermediate class in which `sym` is free, `enclosure`
     *       must be contained in that class (in order to access the `sym proxy stored
     *       in the class).
     *
     *    2. If there is no intermediate class, `enclosure` must be contained
     *       in the class enclosing `sym`.
     *
     *  @return  If there is a non-trait class between `enclosure` and
     *           the owner of `sym`, the largest such class.
     *           Otherwise, if there is a trait between `enclosure` and
     *           the owner of `sym`, the largest such trait.
     *           Otherwise, NoSymbol.
     *
     *  @pre sym.owner.isTerm, (enclosure.isMethod || enclosure.isClass)
     *
     *  The idea of `markFree` is illustrated with an example:
     *
     *  def f(x: int) = {
     *    class C {
     *      class D {
     *        val y = x
     *      }
     *    }
     *  }
     *
     *  In this case `x` is free in the primary constructor of class `C`.
     *  but it is not free in `D`, because after lambda lift the code would be transformed
     *  as follows:
     *
     *  def f(x$0: int) {
     *    class C(x$0: int) {
     *      val x$1 = x$0
     *      class D {
     *        val y = outer.x$1
     *      }
     *    }
     *  }
     */
    private def markFree(sym: Symbol, enclosure: Symbol)(implicit ctx: Context): Symbol = try {
      if (!enclosure.exists) throw new NoPath
      if (enclosure == sym.enclosure) NoSymbol
      else {
        ctx.debuglog(i"mark free: ${sym.showLocated} with owner ${sym.maybeOwner} marked free in $enclosure")
        val intermediate =
          if (enclosure.is(PackageClass)) enclosure
          else markFree(sym, enclosure.enclosure)
        narrowLiftedOwner(enclosure, intermediate orElse sym.enclosingClass)
        if (!intermediate.isRealClass || enclosure.isConstructor) {
          // Constructors and methods nested inside traits get the free variables
          // of the enclosing trait or class.
          // Conversely, local traits do not get free variables.
          if (!enclosure.is(Trait))
            if (symSet(free, enclosure).add(sym)) {
              changedFreeVars = true
              ctx.log(i"$sym is free in $enclosure")
            }
        }
        if (intermediate.isRealClass) intermediate
        else if (enclosure.isRealClass) enclosure
        else if (intermediate.isClass) intermediate
        else if (enclosure.isClass) enclosure
        else NoSymbol
      }
    } catch {
      case ex: NoPath =>
        println(i"error lambda lifting ${ctx.compilationUnit}: $sym is not visible from $enclosure")
        throw ex
    }

    private def markCalled(callee: Symbol, caller: Symbol)(implicit ctx: Context): Unit = {
      ctx.debuglog(i"mark called: $callee of ${callee.owner} is called by $caller in ${caller.owner}")
      assert(isLocal(callee))
      symSet(called, caller) += callee
      if (callee.enclosingClass != caller.enclosingClass) calledFromInner += callee
    }

    private class CollectDependencies extends EnclosingMethodTraverser {
      def traverse(enclosure: Symbol, tree: Tree)(implicit ctx: Context) = try { //debug
        val sym = tree.symbol
        def narrowTo(thisClass: ClassSymbol) = {
          val enclClass = enclosure.enclosingClass
          narrowLiftedOwner(enclosure,
            if (enclClass.isContainedIn(thisClass)) thisClass
            else enclClass) // unknown this reference, play it safe and assume the narrowest possible owner
        }
        tree match {
          case tree: Ident =>
            if (isLocal(sym)) {
              if (sym is Label)
                assert(enclosure == sym.enclosure,
                  i"attempt to refer to label $sym from nested $enclosure")
              else if (sym is Method) markCalled(sym, enclosure)
              else if (sym.isTerm) markFree(sym, enclosure)
            }
            def captureImplicitThis(x: Type): Unit = {
              x match {
                case tr@TermRef(x, _) if (!tr.termSymbol.isStatic) => captureImplicitThis(x)
                case x: ThisType if (!x.tref.typeSymbol.isStaticOwner) => narrowTo(x.tref.typeSymbol.asClass)
                case _ =>
              }
            }
            captureImplicitThis(tree.tpe)
          case tree: Select =>
            if (sym.is(Method) && isLocal(sym)) markCalled(sym, enclosure)
          case tree: This =>
            narrowTo(tree.symbol.asClass)
          case tree: DefDef =>
            if (sym.owner.isTerm && !sym.is(Label))
              liftedOwner(sym) = sym.enclosingPackageClass
                // this will make methods in supercall constructors of top-level classes owned
                // by the enclosing package, which means they will be static.
                // On the other hand, all other methods will be indirectly owned by their
                // top-level class. This avoids possible deadlocks when a static method
                // has to access its enclosing object from the outside.
            else if (sym.isConstructor) {
              if (sym.isPrimaryConstructor && isLocal(sym.owner) && !sym.owner.is(Trait))
                // add a call edge from the constructor of a local non-trait class to
                // the class itself. This is done so that the constructor inherits
                // the free variables of the class.
                symSet(called, sym) += sym.owner

              tree.vparamss.head.find(_.name == nme.OUTER) match {
                case Some(vdef) => outerParam(sym) = vdef.symbol
                case _ =>
              }
            }
          case tree: TypeDef =>
            if (sym.owner.isTerm) liftedOwner(sym) = sym.topLevelClass.owner
          case tree: Template =>
            liftedDefs(tree.symbol.owner) = new mutable.ListBuffer
          case _ =>
        }
        foldOver(enclosure, tree)
      } catch { //debug
        case ex: Exception =>
          println(i"$ex while traversing $tree")
          throw ex
      }
    }

    /** Compute final free variables map `fvs by closing over caller dependencies. */
    private def computeFreeVars()(implicit ctx: Context): Unit =
      do {
        changedFreeVars = false
        for {
          caller <- called.keys
          callee <- called(caller)
          fvs <- free get callee
          fv <- fvs
        } markFree(fv, caller)
      } while (changedFreeVars)

    /** Compute final liftedOwner map by closing over caller dependencies */
    private def computeLiftedOwners()(implicit ctx: Context): Unit =
      do {
        changedLiftedOwner = false
        for {
          caller <- called.keys
          callee <- called(caller)
        } {
          val normalizedCallee = callee.skipConstructor
          val calleeOwner = normalizedCallee.owner
          if (calleeOwner.isTerm) narrowLiftedOwner(caller, liftedOwner(normalizedCallee))
          else {
            assert(calleeOwner.is(Trait))
            // methods nested inside local trait methods cannot be lifted out
            // beyond the trait. Note that we can also call a trait method through
            // a qualifier; in that case no restriction to lifted owner arises.
            if (caller.isContainedIn(calleeOwner))
              narrowLiftedOwner(caller, calleeOwner)
          }
        }
      } while (changedLiftedOwner)

    private def newName(sym: Symbol)(implicit ctx: Context): Name =
      if (sym.isAnonymousFunction && sym.owner.is(Method, butNot = Label))
        (sym.name ++ NJ ++ sym.owner.name).freshened
      else sym.name.freshened

    private def generateProxies()(implicit ctx: Context): Unit =
      for ((owner, freeValues) <- free.toIterator) {
        val newFlags = Synthetic | (if (owner.isClass) ParamAccessor | Private else Param)
        ctx.debuglog(i"free var proxy: ${owner.showLocated}, ${freeValues.toList}%, %")
        proxyMap(owner) = {
          for (fv <- freeValues.toList) yield {
            val proxyName = newName(fv)
            val proxy = ctx.newSymbol(owner, proxyName.asTermName, newFlags, fv.info, coord = fv.coord)
            if (owner.isClass) proxy.enteredAfter(thisTransform)
            (fv, proxy)
          }
        }.toMap
      }

    private def liftedInfo(local: Symbol)(implicit ctx: Context): Type = local.info match {
      case mt @ MethodType(pnames, ptypes) =>
        val ps = proxies(local)
        MethodType(
          ps.map(_.name.asTermName) ++ pnames,
          ps.map(_.info) ++ ptypes,
          mt.resultType)
      case info => info
    }

    private def liftLocals()(implicit ctx: Context): Unit = {
      for ((local, lOwner) <- liftedOwner) {
        val (newOwner, maybeStatic) =
          if (lOwner is Package)  {
            val encClass = local.enclosingClass
            val topClass = local.topLevelClass
              // member of a static object
            if (encClass.isStatic && encClass.isProperlyContainedIn(topClass)) {
               // though the second condition seems weird, it's not true for symbols which are defined in some
               // weird combinations of super calls.
              (encClass, EmptyFlags)
            } else if (encClass.is(ModuleClass, butNot = Package) && encClass.isStatic) // needed to not cause deadlocks in classloader. see t5375.scala
              (encClass, EmptyFlags)
            else (topClass, JavaStatic)
          }
          else (lOwner, EmptyFlags)
        local.copySymDenotation(
          owner = newOwner,
          name = newName(local),
          initFlags = local.flags &~ (InSuperCall | Module) | Private | maybeStatic,
            // drop Module because class is no longer a singleton in the lifted context.
          info = liftedInfo(local)).installAfter(thisTransform)
      }
      for (local <- free.keys)
        if (!liftedOwner.contains(local))
          local.copySymDenotation(info = liftedInfo(local)).installAfter(thisTransform)
    }

    private def init(implicit ctx: Context) = {
      (new CollectDependencies).traverse(NoSymbol, ctx.compilationUnit.tpdTree)
      computeFreeVars()
      computeLiftedOwners()
      generateProxies()(ctx.withPhase(thisTransform.next))
      liftLocals()(ctx.withPhase(thisTransform.next))
    }

    override def prepareForUnit(tree: Tree)(implicit ctx: Context) = {
      val lifter = new LambdaLifter
      lifter.init(ctx.withPhase(thisTransform))
      lifter
    }

    private def currentEnclosure(implicit ctx: Context) =
      ctx.owner.enclosingMethodOrClass

    private def inCurrentOwner(sym: Symbol)(implicit ctx: Context) =
      sym.enclosure == currentEnclosure

    private def proxy(sym: Symbol)(implicit ctx: Context): Symbol = {
      def liftedEnclosure(sym: Symbol) = liftedOwner.getOrElse(sym, sym.enclosure)
      def searchIn(enclosure: Symbol): Symbol = {
        if (!enclosure.exists) {
          def enclosures(encl: Symbol): List[Symbol] =
            if (encl.exists) encl :: enclosures(liftedEnclosure(encl)) else Nil
          throw new IllegalArgumentException(i"Could not find proxy for ${sym.showDcl} in ${sym.ownersIterator.toList}, encl = $currentEnclosure, owners = ${currentEnclosure.ownersIterator.toList}%, %; enclosures = ${enclosures(currentEnclosure)}%, %")
        }
        ctx.debuglog(i"searching for $sym(${sym.owner}) in $enclosure")
        proxyMap get enclosure match {
          case Some(pmap) =>
            pmap get sym match {
              case Some(proxy) => return proxy
              case none =>
            }
          case none =>
        }
        searchIn(liftedEnclosure(enclosure))
      }
      if (inCurrentOwner(sym)) sym else searchIn(currentEnclosure)
    }

    private def memberRef(sym: Symbol)(implicit ctx: Context, info: TransformerInfo): Tree = {
      val clazz = sym.enclosingClass
      val qual =
        if (clazz.isStaticOwner || ctx.owner.enclosingClass == clazz)
          singleton(clazz.thisType)
        else if (ctx.owner.isConstructor)
          outerParam.get(ctx.owner) match {
            case Some(param) => outer.path(clazz, Ident(param.termRef))
            case _ => outer.path(clazz)
          }
        else outer.path(clazz)
      transformFollowingDeep(qual.select(sym))
    }

    private def proxyRef(sym: Symbol)(implicit ctx: Context, info: TransformerInfo): Tree = {
      val psym = proxy(sym)(ctx.withPhase(thisTransform))
      transformFollowingDeep(if (psym.owner.isTerm) ref(psym) else memberRef(psym))
    }

    private def addFreeArgs(sym: Symbol, args: List[Tree])(implicit ctx: Context, info: TransformerInfo) =
      free get sym match {
        case Some(fvs) => fvs.toList.map(proxyRef(_)) ++ args
        case _ => args
      }

    private def addFreeParams(tree: Tree, proxies: List[Symbol])(implicit ctx: Context, info: TransformerInfo): Tree = proxies match {
      case Nil => tree
      case proxies =>
        val sym = tree.symbol
        val freeParamDefs = proxies.map(proxy =>
          transformFollowingDeep(ValDef(proxy.asTerm).withPos(tree.pos)).asInstanceOf[ValDef])
        def proxyInit(field: Symbol, param: Symbol) =
          transformFollowingDeep(memberRef(field).becomes(ref(param)))

        /** Initialize proxy fields from proxy parameters and map `rhs` from fields to parameters */
        def copyParams(rhs: Tree) = {
          val fvs = freeVars(sym.owner)
          val classProxies = fvs.map(proxyOf(sym.owner, _))
          val constrProxies = fvs.map(proxyOf(sym, _))
          ctx.debuglog(i"copy params ${constrProxies.map(_.showLocated)}%, % to ${classProxies.map(_.showLocated)}%, %}")
          seq((classProxies, constrProxies).zipped.map(proxyInit), rhs)
        }

        tree match {
          case tree: DefDef =>
            cpy.DefDef(tree)(
                vparamss = tree.vparamss.map(freeParamDefs ++ _),
                rhs =
                  if (sym.isPrimaryConstructor && !sym.owner.is(Trait)) copyParams(tree.rhs)
                  else tree.rhs)
          case tree: Template =>
            cpy.Template(tree)(body = freeParamDefs ++ tree.body)
        }
    }

    private def liftDef(tree: MemberDef)(implicit ctx: Context, info: TransformerInfo): Tree = {
      val buf = liftedDefs(tree.symbol.owner)
      transformFollowing(rename(tree, tree.symbol.name)).foreachInThicket(buf += _)
      EmptyTree
    }

    private def needsLifting(sym: Symbol) = liftedOwner contains sym

    override def transformIdent(tree: Ident)(implicit ctx: Context, info: TransformerInfo) = {
      val sym = tree.symbol
      tree.tpe match {
        case tpe @ TermRef(prefix, _) =>
          if (prefix eq NoPrefix)
            if (sym.enclosure != currentEnclosure && !sym.isStatic)
              (if (sym is Method) memberRef(sym) else proxyRef(sym)).withPos(tree.pos)
            else if (sym.owner.isClass) // sym was lifted out
              ref(sym).withPos(tree.pos)
            else
              tree
          else if (!prefixIsElidable(tpe)) ref(tpe)
          else tree
        case _ =>
          tree
      }
    }

    override def transformApply(tree: Apply)(implicit ctx: Context, info: TransformerInfo) =
      cpy.Apply(tree)(tree.fun, addFreeArgs(tree.symbol, tree.args)).withPos(tree.pos)

    override def transformClosure(tree: Closure)(implicit ctx: Context, info: TransformerInfo) =
      cpy.Closure(tree)(env = addFreeArgs(tree.meth.symbol, tree.env))

    override def transformDefDef(tree: DefDef)(implicit ctx: Context, info: TransformerInfo) = {
      val sym = tree.symbol
      val paramsAdded =
        if (free.contains(sym)) addFreeParams(tree, proxies(sym)).asInstanceOf[DefDef]
        else tree
      if (needsLifting(sym)) liftDef(paramsAdded)
      else paramsAdded
    }

    override def transformReturn(tree: Return)(implicit ctx: Context, info: TransformerInfo) = tree.expr match {
      case Block(stats, value) =>
        Block(stats, Return(value, tree.from)).withPos(tree.pos)
      case _ =>
        tree
    }

    override def transformTemplate(tree: Template)(implicit ctx: Context, info: TransformerInfo) = {
      val cls = ctx.owner
      val impl = addFreeParams(tree, proxies(cls)).asInstanceOf[Template]
      cpy.Template(impl)(body = impl.body ++ liftedDefs.remove(cls).get)
    }

    override def transformTypeDef(tree: TypeDef)(implicit ctx: Context, info: TransformerInfo) =
      if (needsLifting(tree.symbol)) liftDef(tree) else tree
  }
}
