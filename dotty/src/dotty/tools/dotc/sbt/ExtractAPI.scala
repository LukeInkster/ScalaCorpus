package dotty.tools.dotc
package sbt

import ast.{Trees, tpd}
import core._, core.Decorators._
import Contexts._, Flags._, Phases._, Trees._, Types._, Symbols._
import Names._, NameOps._, StdNames._

import dotty.tools.io.Path
import java.io.PrintWriter

import scala.collection.mutable

/** This phase sends a representation of the API of classes to sbt via callbacks.
 *
 *  This is used by sbt for incremental recompilation.
 *
 *  See the documentation of `ExtractAPICollector`, `ExtractDependencies`,
 *  `ExtractDependenciesCollector` and
 *  http://www.scala-sbt.org/0.13/docs/Understanding-Recompilation.html for more
 *  information on incremental recompilation.
 *
 *  The following flags affect this phase:
 *   -Yforce-sbt-phases
 *   -Ydump-sbt-inc
 *
 *  @see ExtractDependencies
 */
class ExtractAPI extends Phase {
  override def phaseName: String = "sbt-api"

  // SuperAccessors need to be part of the API (see the scripted test
  // `trait-super` for an example where this matters), this is only the case
  // after `PostTyper` (unlike `ExtractDependencies`, the simplication to trees
  // done by `PostTyper` do not affect this phase because it only cares about
  // definitions, and `PostTyper` does not change definitions).
  override def runsAfter = Set(classOf[transform.PostTyper])

  override def run(implicit ctx: Context): Unit = {
    val unit = ctx.compilationUnit
    val dumpInc = ctx.settings.YdumpSbtInc.value
    val forceRun = dumpInc || ctx.settings.YforceSbtPhases.value
    if ((ctx.sbtCallback != null || forceRun) && !unit.isJava) {
      val sourceFile = unit.source.file.file
      val apiTraverser = new ExtractAPICollector
      val source = apiTraverser.apiSource(unit.tpdTree)

      if (dumpInc) {
        // Append to existing file that should have been created by ExtractDependencies
        val pw = new PrintWriter(Path(sourceFile).changeExtension("inc").toFile
          .bufferedWriter(append = true), true)
        try {
          pw.println(DefaultShowAPI(source))
        } finally pw.close()
      }

      if (ctx.sbtCallback != null)
        ctx.sbtCallback.api(sourceFile, source)
    }
  }
}

/** Extracts full (including private members) API representation out of Symbols and Types.
 *
 *  The exact representation used for each type is not important: the only thing
 *  that matters is that a binary-incompatible or source-incompatible change to
 *  the API (for example, changing the signature of a method, or adding a parent
 *  to a class) should result in a change to the API representation so that sbt
 *  can recompile files that depend on this API.
 *
 *  Note that we only records types as they are defined and never "as seen from"
 *  some other prefix because `Types#asSeenFrom` is a complex operation and
 *  doing it for every inherited member would be slow, and because the number
 *  of prefixes can be enormous in some cases:
 *
 *    class Outer {
 *      type T <: S
 *      type S
 *      class A extends Outer { /*...*/ }
 *      class B extends Outer { /*...*/ }
 *      class C extends Outer { /*...*/ }
 *      class D extends Outer { /*...*/ }
 *      class E extends Outer { /*...*/ }
 *    }
 *
 *  `S` might be refined in an arbitrary way inside `A` for example, this
 *  affects the type of `T` as seen from `Outer#A`, so we could record that, but
 *  the class `A` also contains itself as a member, so `Outer#A#A#A#...` is a
 *  valid prefix for `T`. Even if we avoid loops, we still have a combinatorial
 *  explosion of possible prefixes, like `Outer#A#B#C#D#E`.
 *
 *  It is much simpler to record `T` once where it is defined, but that means
 *  that the API representation of `T` may not change even though `T` as seen
 *  from some prefix has changed. This is why in `ExtractDependencies` we need
 *  to traverse used types to not miss dependencies, see the documentation of
 *  `ExtractDependencies#usedTypeTraverser`.
 *
 *  TODO: sbt does not store the full representation that we compute, instead it
 *  hashes parts of it to reduce memory usage, then to see if something changed,
 *  it compares the hashes instead of comparing the representations. We should
 *  investigate whether we can just directly compute hashes in this phase
 *  without going through an intermediate representation, see
 *  http://www.scala-sbt.org/0.13/docs/Understanding-Recompilation.html#Hashing+an+API+representation
 */
private class ExtractAPICollector(implicit val ctx: Context) extends ThunkHolder {
  import tpd._
  import xsbti.api

  /** This cache is necessary for correctness, see the comment about inherited
   *  members in `apiClassStructure`
   */
  private[this] val classLikeCache = new mutable.HashMap[ClassSymbol, api.ClassLike]
  /** This cache is optional, it avoids recomputing representations */
  private[this] val typeCache = new mutable.HashMap[Type, api.Type]

  private[this] object Constants {
    val emptyStringArray = Array[String]()
    val local            = new api.ThisQualifier
    val public           = new api.Public
    val privateLocal     = new api.Private(local)
    val protectedLocal   = new api.Protected(local)
    val unqualified      = new api.Unqualified
    val thisPath         = new api.This
    val emptyType        = new api.EmptyType
    val emptyModifiers   =
      new api.Modifiers(false, false, false, false, false,false, false, false)
  }

  /** Some Dotty types do not have a corresponding type in xsbti.api.* that
   *  represents them. Until this is fixed we can workaround this by using
   *  special annotations that can never appear in the source code to
   *  represent these types.
   *
   *  @param tp      An approximation of the type we're trying to represent
   *  @param marker  A special annotation to differentiate our type
   */
  private def withMarker(tp: api.Type, marker: api.Annotation) =
    new api.Annotated(tp, Array(marker))
  private def marker(name: String) =
    new api.Annotation(new api.Constant(Constants.emptyType, name), Array())
  val orMarker = marker("Or")
  val byNameMarker = marker("ByName")


  /** Extract the API representation of a source file */
  def apiSource(tree: Tree): api.SourceAPI = {
    val classes = new mutable.ListBuffer[api.ClassLike]
    def apiClasses(tree: Tree): Unit = tree match {
      case PackageDef(_, stats) =>
        stats.foreach(apiClasses)
      case tree: TypeDef =>
        classes += apiClass(tree.symbol.asClass)
      case _ =>
    }

    apiClasses(tree)
    forceThunks()
    new api.SourceAPI(Array(), classes.toArray)
  }

  def apiClass(sym: ClassSymbol): api.ClassLike =
    classLikeCache.getOrElseUpdate(sym, computeClass(sym))

  private def computeClass(sym: ClassSymbol): api.ClassLike = {
    import xsbti.api.{DefinitionType => dt}
    val defType =
      if (sym.is(Trait)) dt.Trait
      else if (sym.is(ModuleClass)) {
        if (sym.is(PackageClass)) dt.PackageModule
        else dt.Module
      } else dt.ClassDef

    val selfType = apiType(sym.classInfo.givenSelfType)

    val name = if (sym.is(ModuleClass)) sym.fullName.sourceModuleName else sym.fullName

    val tparams = sym.typeParams.map(tparam => apiTypeParameter(
      tparam.name.toString, tparam.variance,
      tparam.info.bounds.lo, tparam.info.bounds.lo))

    val structure = apiClassStructure(sym)

    new api.ClassLike(
      defType, strict2lzy(selfType), strict2lzy(structure), Constants.emptyStringArray,
      tparams.toArray, name.toString, apiAccess(sym), apiModifiers(sym),
      apiAnnotations(sym).toArray)
  }

  private[this] val LegacyAppClass = ctx.requiredClass("dotty.runtime.LegacyApp")

  def apiClassStructure(csym: ClassSymbol): api.Structure = {
    val cinfo = csym.classInfo

    val bases = linearizedAncestorTypes(cinfo)
    val apiBases = bases.map(apiType)

    // Synthetic methods that are always present do not affect the API
    // and can therefore be ignored.
    def alwaysPresent(s: Symbol) =
      s.isCompanionMethod || (csym.is(ModuleClass) && s.isConstructor)
    val decls = cinfo.decls.filterNot(alwaysPresent).toList
    val apiDecls = apiDefinitions(decls)

    val declSet = decls.toSet
    // TODO: We shouldn't have to compute inherited members. Instead, `Structure`
    // should have a lazy `parentStructures` field.
    val inherited = cinfo.baseClasses
      // We cannot filter out `LegacyApp` because it contains the main method,
      // see the comment about main class discovery in `computeType`.
      .filter(bc => !bc.is(Scala2x) || bc.eq(LegacyAppClass))
      .flatMap(_.classInfo.decls.filterNot(s => s.is(Private) || declSet.contains(s)))
    // Inherited members need to be computed lazily because a class might contain
    // itself as an inherited member, like in `class A { class B extends A }`,
    // this works because of `classLikeCache`
    val apiInherited = lzy(apiDefinitions(inherited).toArray)

    new api.Structure(strict2lzy(apiBases.toArray), strict2lzy(apiDecls.toArray), apiInherited)
  }

  def linearizedAncestorTypes(info: ClassInfo): List[Type] = {
    val ref = info.fullyAppliedRef
    // Note that the ordering of classes in `baseClasses` is important.
    info.baseClasses.tail.map(ref.baseTypeWithArgs)
  }

  def apiDefinitions(defs: List[Symbol]): List[api.Definition] = {
    // The hash generated by sbt for definitions is supposed to be symmetric so
    // we shouldn't have to sort them, but it actually isn't symmetric for
    // definitions which are classes, therefore we need to sort classes to
    // ensure a stable hash.
    // Modules and classes come first and are sorted by name, all other
    // definitions come later and are not sorted.
    object classFirstSort extends Ordering[Symbol] {
      override def compare(a: Symbol, b: Symbol) = {
        val aIsClass = a.isClass
        val bIsClass = b.isClass
        if (aIsClass == bIsClass) {
          if (aIsClass) {
            if (a.is(Module) == b.is(Module))
              a.fullName.toString.compareTo(b.fullName.toString)
            else if (a.is(Module))
              -1
            else
              1
          } else
            0
        } else if (aIsClass)
          -1
        else
          1
      }
    }

    defs.sorted(classFirstSort).map(apiDefinition)
  }

  def apiDefinition(sym: Symbol): api.Definition = {
    if (sym.isClass) {
      apiClass(sym.asClass)
    } else if (sym.isType) {
      apiTypeMember(sym.asType)
    } else if (sym.is(Mutable, butNot = Accessor)) {
      new api.Var(apiType(sym.info), sym.name.toString,
        apiAccess(sym), apiModifiers(sym), apiAnnotations(sym).toArray)
    } else if (sym.isStable) {
      new api.Val(apiType(sym.info), sym.name.toString,
        apiAccess(sym), apiModifiers(sym), apiAnnotations(sym).toArray)
    } else {
      apiDef(sym.asTerm)
    }
  }

  def apiDef(sym: TermSymbol): api.Def = {
    def paramLists(t: Type, start: Int = 0): List[api.ParameterList] = t match {
      case mt @ MethodType(pnames, ptypes) =>
        // TODO: We shouldn't have to work so hard to find the default parameters
        // of a method, Dotty should expose a convenience method for that, see #1143
        val defaults =
          if (sym.is(DefaultParameterized)) {
            val qual =
              if (sym.isClassConstructor)
                sym.owner.companionModule // default getters for class constructors are found in the companion object
              else
                sym.owner
            (0 until pnames.length).map(i => qual.info.member(sym.name.defaultGetterName(start + i)).exists)
          } else
            (0 until pnames.length).map(Function.const(false))
        val params = (pnames, ptypes, defaults).zipped.map((pname, ptype, isDefault) =>
          new api.MethodParameter(pname.toString, apiType(ptype),
            isDefault, api.ParameterModifier.Plain))
        new api.ParameterList(params.toArray, mt.isImplicit) :: paramLists(mt.resultType, params.length)
      case _ =>
        Nil
    }

    val tparams = sym.info match {
      case pt: PolyType =>
        (pt.paramNames, pt.paramBounds).zipped.map((pname, pbounds) =>
          apiTypeParameter(pname.toString, 0, pbounds.lo, pbounds.hi))
      case _ =>
        Nil
    }
    val vparamss = paramLists(sym.info)
    val retTp = sym.info.finalResultType.widenExpr

    new api.Def(vparamss.toArray, apiType(retTp), tparams.toArray,
      sym.name.toString, apiAccess(sym), apiModifiers(sym), apiAnnotations(sym).toArray)
  }

  def apiTypeMember(sym: TypeSymbol): api.TypeMember = {
    val typeParams = Array[api.TypeParameter]()
    val name = sym.name.toString
    val access = apiAccess(sym)
    val modifiers = apiModifiers(sym)
    val as = apiAnnotations(sym)
    val tpe = sym.info

    if (sym.isAliasType)
      new api.TypeAlias(apiType(tpe.bounds.hi), typeParams, name, access, modifiers, as.toArray)
    else {
      assert(sym.isAbstractType)
      new api.TypeDeclaration(apiType(tpe.bounds.lo), apiType(tpe.bounds.hi), typeParams, name, access, modifiers, as.to)
    }
  }

  def apiType(tp: Type): api.Type = {
    typeCache.getOrElseUpdate(tp, computeType(tp))
  }

  private def computeType(tp: Type): api.Type = {
    // TODO: Never dealias. We currently have to dealias because
    // sbt main class discovery relies on the signature of the main
    // method being fully dealiased. See https://github.com/sbt/zinc/issues/102
    val tp2 = if (!tp.isHK) tp.dealias else tp
    tp2 match {
      case NoPrefix | NoType =>
        Constants.emptyType
      case tp: NamedType =>
        val sym = tp.symbol
        // Normalize package prefix to avoid instability of representation
        val prefix = if (sym.isClass && sym.owner.is(Package))
          sym.owner.thisType
        else
          tp.prefix
        new api.Projection(simpleType(prefix), sym.name.toString)
      case TypeApplications.AppliedType(tycon, args) =>
        def processArg(arg: Type): api.Type = arg match {
          case arg @ TypeBounds(lo, hi) => // Handle wildcard parameters
            if (lo.eq(defn.NothingType) && hi.eq(defn.AnyType))
              Constants.emptyType
            else {
              val name = "_"
              val ref = new api.ParameterRef(name)
              new api.Existential(ref,
                Array(apiTypeParameter(name, arg.variance, lo, hi)))
            }
          case _ =>
            apiType(arg)
        }

        val apiTycon = simpleType(tycon)
        val apiArgs = args.map(processArg)
        new api.Parameterized(apiTycon, apiArgs.toArray)
      case rt: RefinedType =>
        val name = rt.refinedName.toString
        val parent = apiType(rt.parent)

        def typeRefinement(name: String, tp: TypeBounds): api.TypeMember = tp match {
          case TypeAlias(alias) =>
            new api.TypeAlias(apiType(alias),
              Array(), name, Constants.public, Constants.emptyModifiers, Array())
          case TypeBounds(lo, hi) =>
            new api.TypeDeclaration(apiType(lo), apiType(hi),
              Array(), name, Constants.public, Constants.emptyModifiers, Array())
        }

        val decl: Array[api.Definition] = rt.refinedInfo match {
          case rinfo: TypeBounds =>
            Array(typeRefinement(name, rinfo))
          case _ =>
            ctx.debuglog(i"sbt-api: skipped structural refinement in $rt")
            Array()
        }
        new api.Structure(strict2lzy(Array(parent)), strict2lzy(decl), strict2lzy(Array()))
      case tp: AndOrType =>
        val parents = List(apiType(tp.tp1), apiType(tp.tp2))

        // TODO: Add a real representation for AndOrTypes in xsbti. The order of
        // types in an `AndOrType` does not change the API, so the API hash should
        // be symmetric.
        val s = new api.Structure(strict2lzy(parents.toArray), strict2lzy(Array()), strict2lzy(Array()))
        if (tp.isAnd)
          s
        else
          withMarker(s, orMarker)
      case ExprType(resultType) =>
        withMarker(apiType(resultType), byNameMarker)
      case ConstantType(constant) =>
        new api.Constant(apiType(constant.tpe), constant.stringValue)
      case AnnotatedType(tpe, annot) =>
        // TODO: Annotation support
        ctx.debuglog(i"sbt-api: skipped annotation in $tp2")
        apiType(tpe)
      case tp: ThisType =>
        apiThis(tp.cls)
      case RefinedThis(binder) =>
        apiThis(binder.typeSymbol)
      case tp: ParamType =>
        new api.ParameterRef(tp.paramName.toString)
      case tp: LazyRef =>
        apiType(tp.ref)
      case tp: TypeVar =>
        apiType(tp.underlying)
      case _ => {
        ctx.warning(i"sbt-api: Unhandled type ${tp.getClass} : $tp")
        Constants.emptyType
      }
    }
  }

  // TODO: Get rid of this method. See https://github.com/sbt/zinc/issues/101
  def simpleType(tp: Type): api.SimpleType = apiType(tp) match {
    case tp: api.SimpleType =>
      tp
    case _ =>
      ctx.debuglog("sbt-api: Not a simple type: " + tp.show)
      Constants.emptyType
  }

  def apiThis(sym: Symbol): api.Singleton = {
    val pathComponents = sym.ownersIterator.takeWhile(!_.isEffectiveRoot)
      .map(s => new api.Id(s.name.toString))
    new api.Singleton(new api.Path(pathComponents.toArray.reverse ++ Array(Constants.thisPath)))
  }

  def apiTypeParameter(name: String, variance: Int, lo: Type, hi: Type): api.TypeParameter =
    new api.TypeParameter(name, Array(), Array(), apiVariance(variance),
      apiType(lo), apiType(hi))

  def apiVariance(v: Int): api.Variance = {
    import api.Variance._
    if (v < 0) Contravariant
    else if (v > 0) Covariant
    else Invariant
  }

  def apiAccess(sym: Symbol): api.Access = {
    // Symbols which are private[foo] do not have the flag Private set,
    // but their `privateWithin` exists, see `Parsers#ParserCommon#normalize`.
    if (!sym.is(Protected | Private) && !sym.privateWithin.exists)
      Constants.public
    else if (sym.is(PrivateLocal))
      Constants.privateLocal
    else if (sym.is(ProtectedLocal))
      Constants.protectedLocal
    else {
      val qualifier =
        if (sym.privateWithin eq NoSymbol)
          Constants.unqualified
        else
          new api.IdQualifier(sym.privateWithin.fullName.toString)
      if (sym.is(Protected))
        new api.Protected(qualifier)
      else
        new api.Private(qualifier)
    }
  }

  def apiModifiers(sym: Symbol): api.Modifiers = {
    val absOver = sym.is(AbsOverride)
    val abs = sym.is(Abstract) || sym.is(Deferred) || absOver
    val over = sym.is(Override) || absOver
    new api.Modifiers(abs, over, sym.is(Final), sym.is(Sealed),
      sym.is(Implicit), sym.is(Lazy), sym.is(Macro), sym.is(SuperAccessor))
  }

  // TODO: Annotation support
  def apiAnnotations(s: Symbol): List[api.Annotation] = Nil
}
