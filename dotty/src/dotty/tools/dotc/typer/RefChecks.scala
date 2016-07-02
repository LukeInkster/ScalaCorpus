package dotty.tools.dotc
package typer

import transform._
import core._
import config._
import Symbols._, SymDenotations._, Types._, Contexts._, Decorators._, Flags._, Names._, NameOps._
import StdNames._, Denotations._, Scopes._, Constants.Constant, SymUtils._
import Annotations._
import util.Positions._
import scala.collection.{ mutable, immutable }
import ast._
import Trees._
import TreeTransforms._
import util.DotClass
import scala.util.{Try, Success, Failure}
import config.{ScalaVersion, NoScalaVersion}
import Decorators._
import typer.ErrorReporting._
import DenotTransformers._
import ValueClasses.isDerivedValueClass

object RefChecks {
  import tpd._

  private def isDefaultGetter(name: Name): Boolean =
    name.isTermName && name.asTermName.defaultGetterIndex >= 0

  private val defaultMethodFilter = new NameFilter {
    def apply(pre: Type, name: Name)(implicit ctx: Context): Boolean = isDefaultGetter(name)
  }

  /** Only one overloaded alternative is allowed to define default arguments */
  private def checkOverloadedRestrictions(clazz: Symbol)(implicit ctx: Context): Unit = {
    // Using the default getters (such as methodName$default$1) as a cheap way of
    // finding methods with default parameters. This way, we can limit the members to
    // those with the DEFAULTPARAM flag, and infer the methods. Looking for the methods
    // directly requires inspecting the parameter list of every one. That modification
    // shaved 95% off the time spent in this method.

    for (
      defaultGetterClass <- List(clazz, clazz.companionModule.moduleClass);
      if defaultGetterClass.isClass
    ) {
      val defaultGetterNames = defaultGetterClass.asClass.memberNames(defaultMethodFilter)
      val defaultMethodNames = defaultGetterNames map (_.asTermName.defaultGetterToMethod)

      for (name <- defaultMethodNames) {
        val methods = clazz.info.member(name).alternatives.map(_.symbol)
        val haveDefaults = methods.filter(_.hasDefaultParams)
        if (haveDefaults.length > 1) {
          val owners = haveDefaults map (_.owner)
          // constructors of different classes are allowed to have defaults
          if (haveDefaults.exists(x => !x.isConstructor) || owners.distinct.size < haveDefaults.size)
            ctx.error(
              "in " + clazz +
                ", multiple overloaded alternatives of " + haveDefaults.head +
                " define default arguments" + (
                  if (owners.forall(_ == clazz)) "."
                  else ".\nThe members with defaults are defined in " + owners.map(_.showLocated).mkString("", " and ", ".")),
              clazz.pos)
        }
      }
    }

    // Check for doomed attempt to overload applyDynamic
    if (clazz derivesFrom defn.DynamicClass) {
      for ((_, m1 :: m2 :: _) <- (clazz.info member nme.applyDynamic).alternatives groupBy (_.symbol.typeParams.length)) {
        ctx.error("implementation restriction: applyDynamic cannot be overloaded except by methods with different numbers of type parameters, e.g. applyDynamic[T1](method: String)(arg: T1) and applyDynamic[T1, T2](method: String)(arg1: T1, arg2: T2)",
          m1.symbol.pos)
      }
    }
  }

  /** Check that final and sealed restrictions on class parents
   *  and that self type of this class conforms to self types of parents.
   *  and required classes.
   */
  private def checkParents(cls: Symbol)(implicit ctx: Context): Unit = cls.info match {
    case cinfo: ClassInfo =>
      def checkSelfConforms(other: TypeRef, category: String, relation: String) = {
        val otherSelf = other.givenSelfType.asSeenFrom(cls.thisType, other.classSymbol)
        if (otherSelf.exists && !(cinfo.selfType <:< otherSelf))
          ctx.error(d"$category: self type ${cinfo.selfType} of $cls does not conform to self type $otherSelf of $relation ${other.classSymbol}", cls.pos)
      }
      for (parent <- cinfo.classParents) {
        val pclazz = parent.classSymbol
        if (pclazz.is(Final))
          ctx.error(d"cannot extend final $pclazz", cls.pos)
        if (pclazz.is(Sealed) && pclazz.associatedFile != cls.associatedFile)
          ctx.error(d"cannot extend sealed $pclazz in different compilation unit", cls.pos)
        checkSelfConforms(parent, "illegal inheritance", "parent")
      }
      for (reqd <- cinfo.givenSelfType.classSymbols)
        checkSelfConforms(reqd.typeRef, "missing requirement", "required")
    case _ =>
  }

  /** Check that a class and its companion object to not both define
   *  a class or module with same name
   */
  private def checkCompanionNameClashes(cls: Symbol)(implicit ctx: Context): Unit =
    if (!(cls.owner is ModuleClass)) {
      val other = cls.owner.linkedClass.info.decl(cls.name)
      if (other.symbol.isClass)
        ctx.error(s"name clash: ${cls.owner} defines $cls" + "\n" +
          s"and its companion ${cls.owner.companionModule} also defines $other",
          cls.pos)
    }

  // Override checking ------------------------------------------------------------

  /** 1. Check all members of class `clazz` for overriding conditions.
   *  That is for overriding member M and overridden member O:
   *
   *    1.1. M must have the same or stronger access privileges as O.
   *    1.2. O must not be final.
   *    1.3. O is deferred, or M has `override` modifier.
   *    1.4. If O is stable, then so is M.
   *     // @M: LIFTED 1.5. Neither M nor O are a parameterized type alias
   *    1.6. If O is a type alias, then M is an alias of O.
   *    1.7. If O is an abstract type then
   *       1.7.1 either M is an abstract type, and M's bounds are sharper than O's bounds.
   *             or M is a type alias or class which conforms to O's bounds.
   *       1.7.2 higher-order type arguments must respect bounds on higher-order type parameters  -- @M
   *              (explicit bounds and those implied by variance annotations) -- @see checkKindBounds
   *    1.8. If O and M are values, then
   *    1.8.1  M's type is a subtype of O's type, or
   *    1.8.2  M is of type []S, O is of type ()T and S <: T, or
   *    1.8.3  M is of type ()S, O is of type []T and S <: T, or
   *    1.9.  If M is a macro def, O cannot be deferred unless there's a concrete method overriding O.
   *    1.10. If M is not a macro def, O cannot be a macro def.
   *  2. Check that only abstract classes have deferred members
   *  3. Check that concrete classes do not have deferred definitions
   *     that are not implemented in a subclass.
   *  4. Check that every member with an `override` modifier
   *     overrides some other member.
   *  TODO check that classes are not overridden
   *  TODO This still needs to be cleaned up; the current version is a staright port of what was there
   *       before, but it looks too complicated and method bodies are far too large.
   */
  private def checkAllOverrides(clazz: Symbol)(implicit ctx: Context): Unit = {
    val self = clazz.thisType
    var hasErrors = false

    case class MixinOverrideError(member: Symbol, msg: String)

    val mixinOverrideErrors = new mutable.ListBuffer[MixinOverrideError]()

    def printMixinOverrideErrors(): Unit = {
      mixinOverrideErrors.toList match {
        case List() =>
        case List(MixinOverrideError(_, msg)) =>
          ctx.error(msg, clazz.pos)
        case MixinOverrideError(member, msg) :: others =>
          val others1 = others.map(_.member).filter(_.name != member.name).distinct
          def othersMsg = {
            val others1 = others.map(_.member)
              .filter(_.name != member.name)
              .map(_.show).distinct
            if (others1.isEmpty) ""
            else i";\n other members with override errors are:: $others1%, %"
          }
          ctx.error(msg + othersMsg, clazz.pos)
      }
    }

    def infoString(sym: Symbol) = infoString0(sym, sym.owner != clazz)
    def infoStringWithLocation(sym: Symbol) = infoString0(sym, true)

    def infoString0(sym: Symbol, showLocation: Boolean) = {
      val sym1 = sym.underlyingSymbol
      def info = self.memberInfo(sym1)
      i"${if (showLocation) sym1.showLocated else sym1}${
        if (sym1.isAliasType) i", which equals ${info.bounds.hi}"
        else if (sym1.isAbstractType) i" with bounds$info"
        else if (sym1.is(Module)) ""
        else if (sym1.isTerm) i" of type $info"
        else ""
      }"
    }

    /* Check that all conditions for overriding `other` by `member`
       * of class `clazz` are met.
       */
    def checkOverride(member: Symbol, other: Symbol): Unit = {
      def memberTp = self.memberInfo(member)
      def otherTp = self.memberInfo(other)

      ctx.debuglog("Checking validity of %s overriding %s".format(member.showLocated, other.showLocated))

      def noErrorType = !memberTp.isErroneous && !otherTp.isErroneous

      def overrideErrorMsg(msg: String): String = {
        val isConcreteOverAbstract =
          (other.owner isSubClass member.owner) && other.is(Deferred) && !member.is(Deferred)
        val addendum =
          if (isConcreteOverAbstract)
            ";\n (Note that %s is abstract,\n  and is therefore overridden by concrete %s)".format(
              infoStringWithLocation(other),
              infoStringWithLocation(member))
          else if (ctx.settings.debug.value)
            err.typeMismatchStr(memberTp, otherTp)
          else ""

        "overriding %s;\n %s %s%s".format(
          infoStringWithLocation(other), infoString(member), msg, addendum)
      }

      def emitOverrideError(fullmsg: String) =
        if (!(hasErrors && member.is(Synthetic) && member.is(Module))) {
          // suppress errors relating toi synthetic companion objects if other override
          // errors (e.g. relating to the companion class) have already been reported.
          if (member.owner == clazz) ctx.error(fullmsg, member.pos)
          else mixinOverrideErrors += new MixinOverrideError(member, fullmsg)
          hasErrors = true
        }

      def overrideError(msg: String) = {
        if (noErrorType)
          emitOverrideError(overrideErrorMsg(msg))
      }

      def autoOverride(sym: Symbol) =
        sym.is(Synthetic) && (
          desugar.isDesugaredCaseClassMethodName(member.name) || // such names are added automatically, can't have an override preset.
          sym.is(Module)) // synthetic companion

      def overrideAccessError() = {
        ctx.log(i"member: ${member.showLocated} ${member.flags}") // DEBUG
        ctx.log(i"other: ${other.showLocated} ${other.flags}") // DEBUG
        val otherAccess = (other.flags & AccessFlags).toString
        overrideError("has weaker access privileges; it should be " +
          (if (otherAccess == "") "public" else "at least " + otherAccess))
      }

      def compatibleTypes =
        if (member.isType) { // intersection of bounds to refined types must be nonempty
          member.is(BaseTypeArg) ||
          (memberTp frozen_<:< otherTp) || {
            val jointBounds = (memberTp.bounds & otherTp.bounds).bounds
            jointBounds.lo frozen_<:< jointBounds.hi
          }
        }
        else
          isDefaultGetter(member.name) || // default getters are not checked for compatibility
          memberTp.overrides(otherTp)

      def domain(sym: Symbol): Set[Name] = sym.info.namedTypeParams.map(_.name)

      //Console.println(infoString(member) + " overrides " + infoString(other) + " in " + clazz);//DEBUG

      // return if we already checked this combination elsewhere
      if (member.owner != clazz) {
        def deferredCheck = member.is(Deferred) || !other.is(Deferred)
        def subOther(s: Symbol) = s derivesFrom other.owner
        def subMember(s: Symbol) = s derivesFrom member.owner

        if (subOther(member.owner) && deferredCheck) {
          //Console.println(infoString(member) + " shadows1 " + infoString(other) " in " + clazz);//DEBUG
          return
        }
        val parentSymbols = clazz.info.parents.map(_.typeSymbol)
        if (parentSymbols exists (p => subOther(p) && subMember(p) && deferredCheck)) {
          //Console.println(infoString(member) + " shadows2 " + infoString(other) + " in " + clazz);//DEBUG
          return
        }
        if (parentSymbols forall (p => subOther(p) == subMember(p))) {
          //Console.println(infoString(member) + " shadows " + infoString(other) + " in " + clazz);//DEBUG
          return
        }
      }

      /* Is the intersection between given two lists of overridden symbols empty? */
      def intersectionIsEmpty(syms1: Iterator[Symbol], syms2: Iterator[Symbol]) = {
        val set2 = syms2.toSet
        !(syms1 exists (set2 contains _))
      }

      // o: public | protected        | package-protected  (aka java's default access)
      // ^-may be overridden by member with access privileges-v
      // m: public | public/protected | public/protected/package-protected-in-same-package-as-o

      if (member.is(Private)) // (1.1)
        overrideError("has weaker access privileges; it should not be private")

      // todo: align accessibility implication checking with isAccessible in Contexts
      val ob = other.accessBoundary(member.owner)
      val mb = member.accessBoundary(member.owner)
      def isOverrideAccessOK = (
        (member.flags & AccessFlags).isEmpty // member is public
        || // - or -
        (!other.is(Protected) || member.is(Protected)) && // if o is protected, so is m, and
        (ob.isContainedIn(mb) || other.is(JavaProtected)) // m relaxes o's access boundary,
        // or o is Java defined and protected (see #3946)
        )
      if (!isOverrideAccessOK) {
        overrideAccessError()
      } else if (other.isClass) {
        overrideError("cannot be used here - class definitions cannot be overridden")
      } else if (!other.is(Deferred) && member.isClass) {
        overrideError("cannot be used here - classes can only override abstract types")
      } else if (other.isEffectivelyFinal) { // (1.2)
        overrideError(i"cannot override final member ${other.showLocated}")
      } else if (!other.is(Deferred) &&
                 !isDefaultGetter(other.name) &&
                 !member.isAnyOverride) {
        // (*) Exclusion for default getters, fixes SI-5178. We cannot assign the Override flag to
        // the default getter: one default getter might sometimes override, sometimes not. Example in comment on ticket.
        if (autoOverride(member))
          member.setFlag(Override)
        else if (member.owner != clazz && other.owner != clazz && !(other.owner derivesFrom member.owner))
          emitOverrideError(
            clazz + " inherits conflicting members:\n  "
              + infoStringWithLocation(other) + "  and\n  " + infoStringWithLocation(member)
              + "\n(Note: this can be resolved by declaring an override in " + clazz + ".)")
        else
          overrideError("needs `override' modifier")
      } else if (other.is(AbsOverride) && other.isIncompleteIn(clazz) && !member.is(AbsOverride)) {
        overrideError("needs `abstract override' modifiers")
      } else if (member.is(Override) && other.is(Accessor) &&
        other.accessedFieldOrGetter.is(Mutable, butNot = Lazy)) {
        // !?! this is not covered by the spec. We need to resolve this either by changing the spec or removing the test here.
        // !!! is there a !?! convention? I'm !!!ing this to make sure it turns up on my searches.
        if (!ctx.settings.overrideVars.value)
          overrideError("cannot override a mutable variable")
      } else if (member.isAnyOverride &&
        !(member.owner.thisType.baseClasses exists (_ isSubClass other.owner)) &&
        !member.is(Deferred) && !other.is(Deferred) &&
        intersectionIsEmpty(member.extendedOverriddenSymbols, other.extendedOverriddenSymbols)) {
        overrideError("cannot override a concrete member without a third member that's overridden by both " +
          "(this rule is designed to prevent ``accidental overrides'')")
      } else if (other.isStable && !member.isStable) { // (1.4)
        overrideError("needs to be a stable, immutable value")
      } else if (member.is(ModuleVal) && !other.isRealMethod && !other.is(Deferred | Lazy)) {
        overrideError("may not override a concrete non-lazy value")
      } else if (member.is(Lazy, butNot = Module) && !other.isRealMethod && !other.is(Lazy)) {
        overrideError("may not override a non-lazy value")
      } else if (other.is(Lazy) && !other.isRealMethod && !member.is(Lazy)) {
        overrideError("must be declared lazy to override a lazy value")
      } else if (other.is(Deferred) && member.is(Macro) && member.extendedOverriddenSymbols.forall(_.is(Deferred))) { // (1.9)
        overrideError("cannot be used here - term macros cannot override abstract methods")
      } else if (other.is(Macro) && !member.is(Macro)) { // (1.10)
        overrideError("cannot be used here - only term macros can override term macros")
      } else if (!compatibleTypes) {
        overrideError("has incompatible type" + err.whyNoMatchStr(memberTp, otherTp))
      } else if (member.isType && domain(member) != domain(other)) {
        overrideError("has different named type parameters: "+
             i"[${domain(member).toList}%, %] instead of [${domain(other).toList}%, %]")
      } else {
        checkOverrideDeprecated()
      }
    }

    /* TODO enable; right now the annotation is scala-private, so cannot be seen
         * here.
         */
    def checkOverrideDeprecated() = { /*
          if (other.hasDeprecatedOverridingAnnotation) {
            val suffix = other.deprecatedOverridingMessage map (": " + _) getOrElse ""
            val msg = s"overriding ${other.fullLocationString} is deprecated$suffix"
            unit.deprecationWarning(member.pos, msg)
          }*/
    }

    try {
      val opc = new OverridingPairs.Cursor(clazz)
      while (opc.hasNext) {
        checkOverride(opc.overriding, opc.overridden)
        opc.next()
      }
    } catch {
      case ex: MergeError =>
        val addendum = ex.tp1 match {
          case tp1: ClassInfo =>
            "\n(Note that having same-named member classes in types of a mixin composition is no longer allowed)"
          case _ => ""
        }
        ctx.error(ex.getMessage + addendum, clazz.pos)
    }
    printMixinOverrideErrors()

    // Verifying a concrete class has nothing unimplemented.
    if (!clazz.is(AbstractOrTrait)) {
      val abstractErrors = new mutable.ListBuffer[String]
      def abstractErrorMessage =
        // a little formatting polish
        if (abstractErrors.size <= 2) abstractErrors mkString " "
        else abstractErrors.tail.mkString(abstractErrors.head + ":\n", "\n", "")

      def abstractClassError(mustBeMixin: Boolean, msg: String): Unit = {
        def prelude = (
          if (clazz.isAnonymousClass || clazz.is(Module)) "object creation impossible"
          else if (mustBeMixin) clazz + " needs to be a mixin"
          else clazz + " needs to be abstract") + ", since"

        if (abstractErrors.isEmpty) abstractErrors ++= List(prelude, msg)
        else abstractErrors += msg
      }

      def hasJavaErasedOverriding(sym: Symbol): Boolean =
        !ctx.erasurePhase.exists || // can't do the test, assume the best
          ctx.atPhase(ctx.erasurePhase.next) { implicit ctx =>
            clazz.info.nonPrivateMember(sym.name).hasAltWith { alt =>
              alt.symbol.is(JavaDefined, butNot = Deferred) &&
                !sym.owner.derivesFrom(alt.symbol.owner) &&
                alt.matches(sym)
            }
          }

      def ignoreDeferred(member: SingleDenotation) =
        member.isType ||
          member.symbol.is(SuperAccessor) || // not yet synthesized
          member.symbol.is(JavaDefined) && hasJavaErasedOverriding(member.symbol)

      // 2. Check that only abstract classes have deferred members
      def checkNoAbstractMembers(): Unit = {
        // Avoid spurious duplicates: first gather any missing members.
        val missing = clazz.thisType.abstractTermMembers.filterNot(ignoreDeferred)
        // Group missing members by the name of the underlying symbol,
        // to consolidate getters and setters.
        val grouped: Map[Name, Seq[SingleDenotation]] = missing groupBy (_.symbol.underlyingSymbol.name)
          // Dotty deviation: Added type annotation for `grouped`.
          // The inferred type is Map[Symbol#ThisName, Seq[SingleDenotation]]
          // but then the definition of isMultiple fails with an error:
          // RefChecks.scala:379: error: type mismatch:
          // found   : underlying.ThisName
          // required: dotty.tools.dotc.core.Symbols.Symbol#ThisName
          //
          //  val isMultiple = grouped.getOrElse(underlying.name(ctx), Nil).size > 1
          //                                                    ^
          // As far as I can see, the complaint is correct, even under the
          // old reading where Symbol#ThisName means x.ThisName forSome { val x }

        val missingMethods = grouped.toList flatMap {
          case (name, syms) =>
            val withoutSetters = syms filterNot (_.symbol.isSetter)
            if (withoutSetters.nonEmpty) withoutSetters else syms
        }

        def stubImplementations: List[String] = {
          // Grouping missing methods by the declaring class
          val regrouped = missingMethods.groupBy(_.symbol.owner).toList
          def membersStrings(members: List[SingleDenotation]) =
            members.sortBy(_.symbol.name.toString).map(_.showDcl + " = ???")

          if (regrouped.tail.isEmpty)
            membersStrings(regrouped.head._2)
          else (regrouped.sortBy("" + _._1.name) flatMap {
            case (owner, members) =>
              ("// Members declared in " + owner.fullName) +: membersStrings(members) :+ ""
          }).init
        }

        // If there are numerous missing methods, we presume they are aware of it and
        // give them a nicely formatted set of method signatures for implementing.
        if (missingMethods.size > 1) {
          abstractClassError(false, "it has " + missingMethods.size + " unimplemented members.")
          val preface =
            """|/** As seen from %s, the missing signatures are as follows.
                 | *  For convenience, these are usable as stub implementations.
                 | */
                 |""".stripMargin.format(clazz)
          abstractErrors += stubImplementations.map("  " + _ + "\n").mkString(preface, "", "")
          return
        }

        for (member <- missing) {
          val memberSym = member.symbol
          def undefined(msg: String) =
            abstractClassError(false, s"${member.showDcl} is not defined $msg")
          val underlying = memberSym.underlyingSymbol

          // Give a specific error message for abstract vars based on why it fails:
          // It could be unimplemented, have only one accessor, or be uninitialized.
          if (underlying.is(Mutable)) {
            val isMultiple = grouped.getOrElse(underlying.name(ctx), Nil).size > 1

            // If both getter and setter are missing, squelch the setter error.
            if (memberSym.isSetter && isMultiple) ()
            else undefined(
              if (memberSym.isSetter) "\n(Note that an abstract var requires a setter in addition to the getter)"
              else if (memberSym.isGetter && !isMultiple) "\n(Note that an abstract var requires a getter in addition to the setter)"
              else err.abstractVarMessage(memberSym))
          } else if (underlying.is(Method)) {
            // If there is a concrete method whose name matches the unimplemented
            // abstract method, and a cursory examination of the difference reveals
            // something obvious to us, let's make it more obvious to them.
            val abstractParams = underlying.info.firstParamTypes
            val matchingName = clazz.info.member(underlying.name).alternatives
            val matchingArity = matchingName filter { m =>
              !m.symbol.is(Deferred) &&
                m.info.firstParamTypes.length == abstractParams.length
            }

            matchingArity match {
              // So far so good: only one candidate method
              case concrete :: Nil =>
                val mismatches =
                  abstractParams.zip(concrete.info.firstParamTypes)
                    .filterNot { case (x, y) => x =:= y }
                mismatches match {
                  // Only one mismatched parameter: say something useful.
                  case (pa, pc) :: Nil =>
                    val abstractSym = pa.typeSymbol
                    val concreteSym = pc.typeSymbol
                    def subclassMsg(c1: Symbol, c2: Symbol) =
                      s": ${c1.showLocated} is a subclass of ${c2.showLocated}, but method parameter types must match exactly."
                    val addendum =
                      if (abstractSym == concreteSym) {
                        val paArgs = pa.argInfos
                        val pcArgs = pc.argInfos
                        val paConstr = pa.withoutArgs(paArgs)
                        val pcConstr = pc.withoutArgs(pcArgs)
                        (paConstr, pcConstr) match {
                          case (TypeRef(pre1, _), TypeRef(pre2, _)) =>
                            if (pre1 =:= pre2) ": their type parameters differ"
                            else ": their prefixes (i.e. enclosing instances) differ"
                          case _ =>
                            ""
                        }
                      } else if (abstractSym isSubClass concreteSym)
                        subclassMsg(abstractSym, concreteSym)
                      else if (concreteSym isSubClass abstractSym)
                        subclassMsg(concreteSym, abstractSym)
                      else ""

                    undefined(s"\n(Note that $pa does not match $pc$addendum)")
                  case xs =>
                    undefined(s"\n(The class implements a member with a different type: ${concrete.showDcl})")
                }
              case Nil =>
                undefined("")
              case concretes =>
                undefined(s"\n(The class implements members with different types: ${concretes.map(_.showDcl)}%\n  %)")
            }
          } else undefined("")
        }
      }

      // 3. Check that concrete classes do not have deferred definitions
      // that are not implemented in a subclass.
      // Note that this is not the same as (2); In a situation like
      //
      // class C { def m: Int = 0}
      // class D extends C { def m: Int }
      //
      // (3) is violated but not (2).
      def checkNoAbstractDecls(bc: Symbol): Unit = {
        for (decl <- bc.info.decls) {
          if (decl.is(Deferred) && !ignoreDeferred(decl)) {
            val impl = decl.matchingMember(clazz.thisType)
            if (impl == NoSymbol || (decl.owner isSubClass impl.owner)) {
              val impl1 = clazz.thisType.nonPrivateMember(decl.name) // DEBUG
              ctx.log(i"${impl1}: ${impl1.info}") // DEBUG
              ctx.log(i"${clazz.thisType.memberInfo(decl)}") // DEBUG
              abstractClassError(false, "there is a deferred declaration of " + infoString(decl) +
                " which is not implemented in a subclass" + err.abstractVarMessage(decl))
            }
          }
        }
        if (bc.asClass.superClass.is(Abstract))
          checkNoAbstractDecls(bc.asClass.superClass)
      }

      checkNoAbstractMembers()
      if (abstractErrors.isEmpty)
        checkNoAbstractDecls(clazz)

      if (abstractErrors.nonEmpty)
        ctx.error(abstractErrorMessage, clazz.pos)
    } else if (clazz.is(Trait) && !(clazz derivesFrom defn.AnyValClass)) {
      // For non-AnyVal classes, prevent abstract methods in interfaces that override
      // final members in Object; see #4431
      for (decl <- clazz.info.decls) {
        // Have to use matchingSymbol, not a method involving overridden symbols,
        // because the scala type system understands that an abstract method here does not
        // override a concrete method in Object. The jvm, however, does not.
        val overridden = decl.matchingDecl(defn.ObjectClass, defn.ObjectType)
        if (overridden.is(Final))
          ctx.error("trait cannot redefine final method from class AnyRef", decl.pos)
      }
    }

    /* Returns whether there is a symbol declared in class `inclazz`
       * (which must be different from `clazz`) whose name and type
       * seen as a member of `class.thisType` matches `member`'s.
       */
    def hasMatchingSym(inclazz: Symbol, member: Symbol): Boolean = {

      def isSignatureMatch(sym: Symbol) = !sym.isTerm ||
        clazz.thisType.memberInfo(sym).matchesLoosely(member.info)

      /* The rules for accessing members which have an access boundary are more
         * restrictive in java than scala.  Since java has no concept of package nesting,
         * a member with "default" (package-level) access can only be accessed by members
         * in the exact same package.  Example:
         *
         *   package a.b;
         *   public class JavaClass { void foo() { } }
         *
         * The member foo() can be accessed only from members of package a.b, and not
         * nested packages like a.b.c.  In the analogous scala class:
         *
         *   package a.b
         *   class ScalaClass { private[b] def foo() = () }
         *
         * The member IS accessible to classes in package a.b.c.  The javaAccessCheck logic
         * is restricting the set of matching signatures according to the above semantics.
         */
      def javaAccessCheck(sym: Symbol) = (
        !inclazz.is(JavaDefined) // not a java defined member
        || !sym.privateWithin.exists // no access boundary
        || sym.is(Protected) // marked protected in java, thus accessible to subclasses
        || sym.privateWithin == member.enclosingPackageClass // exact package match
        )
      def classDecls = inclazz.info.nonPrivateDecl(member.name)

      (inclazz != clazz) &&
        classDecls.hasAltWith(d => isSignatureMatch(d.symbol) && javaAccessCheck(d.symbol))
    }

    // 4. Check that every defined member with an `override` modifier overrides some other member.
    for (member <- clazz.info.decls)
      if (member.isAnyOverride && !(clazz.thisType.baseClasses exists (hasMatchingSym(_, member)))) {
        // for (bc <- clazz.info.baseClasses.tail) Console.println("" + bc + " has " + bc.info.decl(member.name) + ":" + bc.info.decl(member.name).tpe);//DEBUG

        val nonMatching = clazz.info.member(member.name).altsWith(alt => alt.owner != clazz && !alt.is(Final))
        def issueError(suffix: String) =
          ctx.error(i"$member overrides nothing$suffix", member.pos)
        nonMatching match {
          case Nil =>
            issueError("")
          case ms =>
            val superSigs = ms.map(_.showDcl).mkString("\n")
            issueError(s".\nNote: the super classes of ${member.owner} contain the following, non final members named ${member.name}:\n${superSigs}")
        }
        member.resetFlag(Override)
        member.resetFlag(AbsOverride)
      }
  }

  // Note: if a symbol has both @deprecated and @migration annotations and both
  // warnings are enabled, only the first one checked here will be emitted.
  // I assume that's a consequence of some code trying to avoid noise by suppressing
  // warnings after the first, but I think it'd be better if we didn't have to
  // arbitrarily choose one as more important than the other.
  private def checkUndesiredProperties(sym: Symbol, pos: Position)(implicit ctx: Context): Unit = {
    // If symbol is deprecated, and the point of reference is not enclosed
    // in either a deprecated member or a scala bridge method, issue a warning.
    if (sym.isDeprecated && !ctx.owner.ownersIterator.exists(_.isDeprecated)) {
      ctx.deprecationWarning("%s%s is deprecated%s".format(
        sym, sym.showLocated, sym.deprecationMessage map (": " + _) getOrElse "", pos))
    }
    // Similar to deprecation: check if the symbol is marked with @migration
    // indicating it has changed semantics between versions.
    if (sym.hasAnnotation(defn.MigrationAnnot) && ctx.settings.Xmigration.value != NoScalaVersion) {
      val symVersion: scala.util.Try[ScalaVersion] = sym.migrationVersion.get
      val changed = symVersion match {
        case scala.util.Success(v) =>
          ctx.settings.Xmigration.value < v
        case Failure(ex) =>
          ctx.warning(s"${sym.showLocated} has an unparsable version number: ${ex.getMessage()}", pos)
          false
      }
      if (changed)
        ctx.warning(s"${sym.showLocated} has changed semantics in version $symVersion:\n${sym.migrationMessage.get}")
    }
    /*  (Not enabled yet)
       *  See an explanation of compileTimeOnly in its scaladoc at scala.annotation.compileTimeOnly.
       *
      if (sym.isCompileTimeOnly) {
        def defaultMsg =
          sm"""Reference to ${sym.fullLocationString} should not have survived past type checking,
              |it should have been processed and eliminated during expansion of an enclosing macro."""
        // The getOrElse part should never happen, it's just here as a backstop.
        ctx.error(sym.compileTimeOnlyMessage getOrElse defaultMsg, pos)
      }*/
  }

  /** Check that a deprecated val or def does not override a
   *  concrete, non-deprecated method.  If it does, then
   *  deprecation is meaningless.
   */
  private def checkDeprecatedOvers(tree: Tree)(implicit ctx: Context): Unit = {
    val symbol = tree.symbol
    if (symbol.isDeprecated) {
      val concrOvers =
        symbol.allOverriddenSymbols.filter(sym =>
          !sym.isDeprecated && !sym.is(Deferred))
      if (!concrOvers.isEmpty)
        ctx.deprecationWarning(
          symbol.toString + " overrides concrete, non-deprecated symbol(s):" +
            concrOvers.map(_.name.decode).mkString("    ", ", ", ""), tree.pos)
    }
  }

  /** Verify classes extending AnyVal meet the requirements */
  private def checkDerivedValueClass(clazz: Symbol, stats: List[Tree])(implicit ctx: Context) = {
    def checkValueClassMember(stat: Tree) = stat match {
      case _: ValDef if !stat.symbol.is(ParamAccessor) =>
        ctx.error(s"value class may not define non-parameter field", stat.pos)
      case _: DefDef if stat.symbol.isConstructor =>
        ctx.error(s"value class may not define secondary constructor", stat.pos)
      case _: MemberDef | _: Import | EmptyTree =>
      // ok
      case _ =>
        ctx.error(s"value class may not contain initialization statements", stat.pos)
    }
    if (isDerivedValueClass(clazz)) {
      if (clazz.is(Trait))
        ctx.error("Only classes (not traits) are allowed to extend AnyVal", clazz.pos)
      if (clazz.is(Abstract))
        ctx.error("`abstract' modifier cannot be used with value classes", clazz.pos)
      if (!clazz.isStatic)
        ctx.error(s"value class may not be a ${if (clazz.owner.isTerm) "local class" else "member of another class"}", clazz.pos)
      else {
        val clParamAccessors = clazz.asClass.paramAccessors.filter(sym => sym.isTerm && !sym.is(Method))
        clParamAccessors match {
          case List(param) =>
            if (param.is(Mutable))
              ctx.error("value class parameter must not be a var", param.pos)
          case _ =>
            ctx.error("value class needs to have exactly one val parameter", clazz.pos)
        }
      }
      stats.foreach(checkValueClassMember)
    }
  }

  type LevelAndIndex = immutable.Map[Symbol, (LevelInfo, Int)]

  class OptLevelInfo extends DotClass {
    def levelAndIndex: LevelAndIndex = Map()
    def enterReference(sym: Symbol, pos: Position): Unit = ()
  }

  /** A class to help in forward reference checking */
  class LevelInfo(outerLevelAndIndex: LevelAndIndex, stats: List[Tree])(implicit ctx: Context)
  extends OptLevelInfo {
    override val levelAndIndex: LevelAndIndex =
      ((outerLevelAndIndex, 0) /: stats) {(mi, stat) =>
        val (m, idx) = mi
        val m1 = stat match {
          case stat: MemberDef => m.updated(stat.symbol, (this, idx))
          case _ => m
        }
        (m1, idx + 1)
      }._1
    var maxIndex: Int = Int.MinValue
    var refPos: Position = _
    var refSym: Symbol = _

    override def enterReference(sym: Symbol, pos: Position): Unit =
      if (sym.exists && sym.owner.isTerm)
        levelAndIndex.get(sym) match {
          case Some((level, idx)) if (level.maxIndex < idx) =>
            level.maxIndex = idx
            level.refPos = pos
            level.refSym = sym
          case _ =>
        }
  }

  val NoLevelInfo = new OptLevelInfo()
}
import RefChecks._

/** Post-attribution checking and transformation, which fulfills the following roles
 *
 *  1. This phase performs the following checks.
 *
 *  - only one overloaded alternative defines default arguments
 *  - applyDynamic methods are not overloaded
 *  - all overrides conform to rules laid down by `checkAllOverrides`.
 *  - any value classes conform to rules laid down by `checkDerivedValueClass`.
 *  - this(...) constructor calls do not forward reference other definitions in their block (not even lazy vals).
 *  - no forward reference in a local block jumps over a non-lazy val definition.
 *  - a class and its companion object do not both define a class or module with the same name.
 *
 *  2. It warns about references to symbols labeled deprecated or migration.

 *  3. It performs the following transformations:
 *
 *  - if (true) A else B  --> A
 *    if (false) A else B --> B
 *  - macro definitions are eliminated.
 *
 *  4. It makes members not private where necessary. The following members
 *  cannot be private in the Java model:
 *   - term members of traits
 *   - the primary constructor of a value class
 *   - the parameter accessor of a value class
 *   - members accessed from an inner or companion class.
 *  All these members are marked as NotJavaPrivate.
 *  Unlike in Scala 2.x not-private members keep their name. It is
 *  up to the backend to find a unique expanded name for them. The
 *  rationale to do name changes that late is that they are very fragile.

 *  todo: But RefChecks is not done yet. It's still a somewhat dirty port from the Scala 2 version.
 *  todo: move untrivial logic to their own mini-phases
 */
class RefChecks extends MiniPhase { thisTransformer =>

  import tpd._

  override def phaseName: String = "refchecks"

  val treeTransform = new Transform(NoLevelInfo)

  class Transform(currentLevel: RefChecks.OptLevelInfo = RefChecks.NoLevelInfo) extends TreeTransform {
    def phase = thisTransformer

    override def prepareForStats(trees: List[Tree])(implicit ctx: Context) = {
      // println(i"preparing for $trees%; %, owner = ${ctx.owner}")
      if (ctx.owner.isTerm) new Transform(new LevelInfo(currentLevel.levelAndIndex, trees))
      else this
    }

    override def transformStats(trees: List[Tree])(implicit ctx: Context, info: TransformerInfo): List[Tree] = trees

    override def transformValDef(tree: ValDef)(implicit ctx: Context, info: TransformerInfo) = {
      checkDeprecatedOvers(tree)
      val sym = tree.symbol
      if (sym.exists && sym.owner.isTerm && !sym.is(Lazy))
        currentLevel.levelAndIndex.get(sym) match {
          case Some((level, symIdx)) if symIdx < level.maxIndex =>
            ctx.debuglog("refsym = " + level.refSym)
            ctx.error(s"forward reference extends over definition of $sym", level.refPos)
          case _ =>
        }
      tree
    }

    override def transformDefDef(tree: DefDef)(implicit ctx: Context, info: TransformerInfo) = {
      checkDeprecatedOvers(tree)
      if (tree.symbol is Macro) EmptyTree else tree
    }

    override def transformTemplate(tree: Template)(implicit ctx: Context, info: TransformerInfo) = try {
      val cls = ctx.owner
      checkOverloadedRestrictions(cls)
      checkParents(cls)
      checkCompanionNameClashes(cls)
      checkAllOverrides(cls)
      checkDerivedValueClass(cls, tree.body)
      tree
    } catch {
      case ex: MergeError =>
        ctx.error(ex.getMessage, tree.pos)
        tree
    }

    override def transformTypeTree(tree: TypeTree)(implicit ctx: Context, info: TransformerInfo) = {
      if (!tree.original.isEmpty)
        tree.tpe.foreachPart {
          case tp: NamedType => checkUndesiredProperties(tp.symbol, tree.pos)
          case _ =>
        }
      tree
    }

    override def transformIdent(tree: Ident)(implicit ctx: Context, info: TransformerInfo) = {
      checkUndesiredProperties(tree.symbol, tree.pos)
      currentLevel.enterReference(tree.symbol, tree.pos)
      tree
    }

    override def transformSelect(tree: Select)(implicit ctx: Context, info: TransformerInfo) = {
      checkUndesiredProperties(tree.symbol, tree.pos)
      tree
    }

    override def transformApply(tree: Apply)(implicit ctx: Context, info: TransformerInfo) = {
      if (isSelfConstrCall(tree)) {
        assert(currentLevel.isInstanceOf[LevelInfo], ctx.owner + "/" + i"$tree")
        val level = currentLevel.asInstanceOf[LevelInfo]
        if (level.maxIndex > 0) {
          // An implementation restriction to avoid VerifyErrors and lazyvals mishaps; see SI-4717
          ctx.debuglog("refsym = " + level.refSym)
          ctx.error("forward reference not allowed from self constructor invocation", level.refPos)
        }
      }
      tree
    }

    override def transformIf(tree: If)(implicit ctx: Context, info: TransformerInfo) =
      tree.cond.tpe match {
        case ConstantType(value) => if (value.booleanValue) tree.thenp else tree.elsep
        case _ => tree
      }

    override def transformNew(tree: New)(implicit ctx: Context, info: TransformerInfo) = {
      currentLevel.enterReference(tree.tpe.typeSymbol, tree.pos)
      tree
    }

    override def transformTypeApply(tree: tpd.TypeApply)(implicit ctx: Context, info: TransformerInfo): tpd.Tree = {
      tree.fun match {
        case fun@Select(qual, selector) =>
          val sym = tree.symbol

          if (sym == defn.Any_isInstanceOf) {
            val argType = tree.args.head.tpe
            val qualCls = qual.tpe.widen.classSymbol
            val argCls = argType.classSymbol
            if (qualCls.isPrimitiveValueClass && !argCls.isPrimitiveValueClass) ctx.error("isInstanceOf cannot test if value types are references", tree.pos)
          }
        case _ =>
      }
      tree
    }
  }
}

/* todo: rewrite and re-enable

// Comparison checking -------------------------------------------------------

    object normalizeAll extends TypeMap {
      def apply(tp: Type) = mapOver(tp).normalize
    }

    def checkImplicitViewOptionApply(pos: Position, fn: Tree, args: List[Tree]): Unit = if (settings.lint) (fn, args) match {
      case (tap@TypeApply(fun, targs), List(view: ApplyImplicitView)) if fun.symbol == currentRun.runDefinitions.Option_apply =>
        unit.warning(pos, s"Suspicious application of an implicit view (${view.fun}) in the argument to Option.apply.") // SI-6567
      case _ =>
    }

    private def isObjectOrAnyComparisonMethod(sym: Symbol) = sym match {
      case Object_eq | Object_ne | Object_== | Object_!= | Any_== | Any_!= => true
      case _                                                               => false
    }
    /** Check the sensibility of using the given `equals` to compare `qual` and `other`. */
    private def checkSensibleEquals(pos: Position, qual: Tree, name: Name, sym: Symbol, other: Tree) = {
      def isReferenceOp = sym == Object_eq || sym == Object_ne
      def isNew(tree: Tree) = tree match {
        case Function(_, _) | Apply(Select(New(_), nme.CONSTRUCTOR), _) => true
        case _ => false
      }
      def underlyingClass(tp: Type): Symbol = {
        val sym = tp.widen.typeSymbol
        if (sym.isAbstractType) underlyingClass(sym.info.bounds.hi)
        else sym
      }
      val actual   = underlyingClass(other.tpe)
      val receiver = underlyingClass(qual.tpe)
      def onTrees[T](f: List[Tree] => T) = f(List(qual, other))
      def onSyms[T](f: List[Symbol] => T) = f(List(receiver, actual))

      // @MAT normalize for consistency in error message, otherwise only part is normalized due to use of `typeSymbol`
      def typesString = normalizeAll(qual.tpe.widen)+" and " + normalizeAll(other.tpe.widen)

      /* Symbols which limit the warnings we can issue since they may be value types */
      val isMaybeValue = Set[Symbol](AnyClass, AnyRefClass, AnyValClass, ObjectClass, ComparableClass, JavaSerializableClass)

      // Whether def equals(other: Any) has known behavior: it is the default
      // inherited from java.lang.Object, or it is a synthetically generated
      // case equals.  TODO - more cases are warnable if the target is a synthetic
      // equals.
      def isUsingWarnableEquals = {
        val m = receiver.info.member(nme.equals_)
        ((m == Object_equals) || (m == Any_equals) || isMethodCaseEquals(m))
      }
      def isMethodCaseEquals(m: Symbol) = m.isSynthetic && m.owner.isCase
      def isCaseEquals = isMethodCaseEquals(receiver.info.member(nme.equals_))
      // Whether this == or != is one of those defined in Any/AnyRef or an overload from elsewhere.
      def isUsingDefaultScalaOp = sym == Object_== || sym == Object_!= || sym == Any_== || sym == Any_!=
      def haveSubclassRelationship = (actual isSubClass receiver) || (receiver isSubClass actual)

      // Whether the operands+operator represent a warnable combo (assuming anyrefs)
      // Looking for comparisons performed with ==/!= in combination with either an
      // equals method inherited from Object or a case class synthetic equals (for
      // which we know the logic.)
      def isWarnable           = isReferenceOp || (isUsingDefaultScalaOp && isUsingWarnableEquals)
      def isEitherNullable     = (NullTpe <:< receiver.info) || (NullTpe <:< actual.info)
      def isEitherValueClass   = actual.isDerivedValueClass || receiver.isDerivedValueClass
      def isBoolean(s: Symbol) = unboxedValueClass(s) == BooleanClass
      def isUnit(s: Symbol)    = unboxedValueClass(s) == UnitClass
      def isNumeric(s: Symbol) = isNumericValueClass(unboxedValueClass(s)) || isAnyNumber(s)
      def isScalaNumber(s: Symbol) = s isSubClass ScalaNumberClass
      def isJavaNumber(s: Symbol)  = s isSubClass JavaNumberClass
      // includes java.lang.Number if appropriate [SI-5779]
      def isAnyNumber(s: Symbol)     = isScalaNumber(s) || isJavaNumber(s)
      def isMaybeAnyValue(s: Symbol) = isPrimitiveValueClass(unboxedValueClass(s)) || isMaybeValue(s)
      // used to short-circuit unrelatedTypes check if both sides are special
      def isSpecial(s: Symbol) = isMaybeAnyValue(s) || isAnyNumber(s)
      val nullCount            = onSyms(_ filter (_ == NullClass) size)
      def isNonsenseValueClassCompare = (
           !haveSubclassRelationship
        && isUsingDefaultScalaOp
        && isEitherValueClass
        && !isCaseEquals
      )

      // Have we already determined that the comparison is non-sensible? I mean, non-sensical?
      var isNonSensible = false

      def nonSensibleWarning(what: String, alwaysEqual: Boolean) = {
        val msg = alwaysEqual == (name == nme.EQ || name == nme.eq)
        unit.warning(pos, s"comparing $what using `${name.decode}' will always yield $msg")
        isNonSensible = true
      }
      def nonSensible(pre: String, alwaysEqual: Boolean) =
        nonSensibleWarning(s"${pre}values of types $typesString", alwaysEqual)
      def nonSensiblyEq() = nonSensible("", alwaysEqual = true)
      def nonSensiblyNeq() = nonSensible("", alwaysEqual = false)
      def nonSensiblyNew() = nonSensibleWarning("a fresh object", alwaysEqual = false)

      def unrelatedMsg = name match {
        case nme.EQ | nme.eq => "never compare equal"
        case _               => "always compare unequal"
      }
      def unrelatedTypes() = if (!isNonSensible) {
        val weaselWord = if (isEitherValueClass) "" else " most likely"
        unit.warning(pos, s"$typesString are unrelated: they will$weaselWord $unrelatedMsg")
      }

      if (nullCount == 2) // null == null
        nonSensiblyEq()
      else if (nullCount == 1) {
        if (onSyms(_ exists isPrimitiveValueClass)) // null == 5
          nonSensiblyNeq()
        else if (onTrees( _ exists isNew)) // null == new AnyRef
          nonSensiblyNew()
      }
      else if (isBoolean(receiver)) {
        if (!isBoolean(actual) && !isMaybeValue(actual))    // true == 5
          nonSensiblyNeq()
      }
      else if (isUnit(receiver)) {
        if (isUnit(actual)) // () == ()
          nonSensiblyEq()
        else if (!isUnit(actual) && !isMaybeValue(actual))  // () == "abc"
          nonSensiblyNeq()
      }
      else if (isNumeric(receiver)) {
        if (!isNumeric(actual))
          if (isUnit(actual) || isBoolean(actual) || !isMaybeValue(actual))   // 5 == "abc"
            nonSensiblyNeq()
      }
      else if (isWarnable && !isCaseEquals) {
        if (isNew(qual)) // new X == y
          nonSensiblyNew()
        else if (isNew(other) && (receiver.isEffectivelyFinal || isReferenceOp))   // object X ; X == new Y
          nonSensiblyNew()
        else if (receiver.isEffectivelyFinal && !(receiver isSubClass actual) && !actual.isRefinementClass) {  // object X, Y; X == Y
          if (isEitherNullable)
            nonSensible("non-null ", false)
          else
            nonSensiblyNeq()
        }
      }

      // warn if one but not the other is a derived value class
      // this is especially important to enable transitioning from
      // regular to value classes without silent failures.
      if (isNonsenseValueClassCompare)
        unrelatedTypes()
      // possibleNumericCount is insufficient or this will warn on e.g. Boolean == j.l.Boolean
      else if (isWarnable && nullCount == 0 && !(isSpecial(receiver) && isSpecial(actual))) {
        // better to have lubbed and lost
        def warnIfLubless(): Unit = {
          val common = global.lub(List(actual.tpe, receiver.tpe))
          if (ObjectTpe <:< common)
            unrelatedTypes()
        }
        // warn if actual has a case parent that is not same as receiver's;
        // if actual is not a case, then warn if no common supertype, as below
        if (isCaseEquals) {
          def thisCase = receiver.info.member(nme.equals_).owner
          actual.info.baseClasses.find(_.isCase) match {
            case Some(p) if p != thisCase => nonSensible("case class ", false)
            case None =>
              // stronger message on (Some(1) == None)
              //if (receiver.isCase && receiver.isEffectivelyFinal && !(receiver isSubClass actual)) nonSensiblyNeq()
              //else
              // if a class, it must be super to thisCase (and receiver) since not <: thisCase
              if (!actual.isTrait && !(receiver isSubClass actual)) nonSensiblyNeq()
              else if (!haveSubclassRelationship) warnIfLubless()
            case _ =>
          }
        }
        // warn only if they have no common supertype below Object
        else if (!haveSubclassRelationship) {
          warnIfLubless()
        }
      }
    }
    /** Sensibility check examines flavors of equals. */
    def checkSensible(pos: Position, fn: Tree, args: List[Tree]) = fn match {
      case Select(qual, name @ (nme.EQ | nme.NE | nme.eq | nme.ne)) if args.length == 1 && isObjectOrAnyComparisonMethod(fn.symbol) =>
        checkSensibleEquals(pos, qual, name, fn.symbol, args.head)
      case _ =>
    }
*/

/* --------------- Overflow -------------------------------------------------
 *

  def accessFlagsToString(sym: Symbol) = flagsToString(
    sym getFlag (PRIVATE | PROTECTED),
    if (sym.hasAccessBoundary) "" + sym.privateWithin.name else ""
  )

  def overridesTypeInPrefix(tp1: Type, tp2: Type, prefix: Type): Boolean = (tp1.dealiasWiden, tp2.dealiasWiden) match {
    case (MethodType(List(), rtp1), NullaryMethodType(rtp2)) =>
      rtp1 <:< rtp2
    case (NullaryMethodType(rtp1), MethodType(List(), rtp2)) =>
      rtp1 <:< rtp2
    case (TypeRef(_, sym, _),  _) if sym.isModuleClass =>
      overridesTypeInPrefix(NullaryMethodType(tp1), tp2, prefix)
    case _ =>
      def classBoundAsSeen(tp: Type) = tp.typeSymbol.classBound.asSeenFrom(prefix, tp.typeSymbol.owner)

      (tp1 <:< tp2) || (  // object override check
        tp1.typeSymbol.isModuleClass && tp2.typeSymbol.isModuleClass && {
          val cb1 = classBoundAsSeen(tp1)
          val cb2 = classBoundAsSeen(tp2)
          (cb1 <:< cb2) && {
            log("Allowing %s to override %s because %s <:< %s".format(tp1, tp2, cb1, cb2))
            true
          }
        }
      )
  }
    private def checkTypeRef(tp: Type, tree: Tree, skipBounds: Boolean)(implicit ctx: Context) = tp match {
      case TypeRef(pre, sym, args) =>
        tree match {
          case tt: TypeTree if tt.original == null => // SI-7783 don't warn about inferred types
                                                      // FIXME: reconcile this check with one in resetAttrs
          case _ => checkUndesiredProperties(sym, tree.pos)
        }
        if (sym.isJavaDefined)
          sym.typeParams foreach (_.cookJavaRawInfo())
        if (!tp.isHigherKinded && !skipBounds)
          checkBounds(tree, pre, sym.owner, sym.typeParams, args)
      case _ =>
    }

    private def checkTypeRefBounds(tp: Type, tree: Tree) = {
      var skipBounds = false
      tp match {
        case AnnotatedType(ann :: Nil, underlying) if ann.symbol == UncheckedBoundsClass =>
          skipBounds = true
          underlying
        case TypeRef(pre, sym, args) =>
          if (!tp.isHigherKinded && !skipBounds)
            checkBounds(tree, pre, sym.owner, sym.typeParams, args)
          tp
        case _ =>
          tp
      }
    }

    private def checkAnnotations(tpes: List[Type], tree: Tree) = tpes foreach { tp =>
      checkTypeRef(tp, tree, skipBounds = false)
      checkTypeRefBounds(tp, tree)
    }
    private def doTypeTraversal(tree: Tree)(f: Type => Unit) = if (!inPattern) tree.tpe foreach f

    private def applyRefchecksToAnnotations(tree: Tree)(implicit ctx: Context): Unit = {
      def applyChecks(annots: List[Annotation]) = {
        checkAnnotations(annots map (_.atp), tree)
        transformTrees(annots flatMap (_.args))
      }

      tree match {
        case m: MemberDef =>
          val sym = m.symbol
          applyChecks(sym.annotations)
          // validate implicitNotFoundMessage
          analyzer.ImplicitNotFoundMsg.check(sym) foreach { warn =>
            unit.warning(tree.pos, f"Invalid implicitNotFound message for ${sym}%s${sym.locationString}%s:%n$warn")
          }

        case tpt@TypeTree() =>
          if (tpt.original != null) {
            tpt.original foreach {
              case dc@TypeTreeWithDeferredRefCheck() =>
                applyRefchecksToAnnotations(dc.check()) // #2416
              case _ =>
            }
          }

          doTypeTraversal(tree) {
            case tp @ AnnotatedType(annots, _)  =>
              applyChecks(annots)
            case tp =>
          }
        case _ =>
      }
    }

    private def transformCaseApply(tree: Tree, ifNot: => Unit) = {
      val sym = tree.symbol

      def isClassTypeAccessible(tree: Tree): Boolean = tree match {
        case TypeApply(fun, targs) =>
          isClassTypeAccessible(fun)
        case Select(module, apply) =>
          ( // SI-4859 `CaseClass1().InnerCaseClass2()` must not be rewritten to `new InnerCaseClass2()`;
            //          {expr; Outer}.Inner() must not be rewritten to `new Outer.Inner()`.
            treeInfo.isQualifierSafeToElide(module) &&
            // SI-5626 Classes in refinement types cannot be constructed with `new`. In this case,
            // the companion class is actually not a ClassSymbol, but a reference to an abstract type.
            module.symbol.companionClass.isClass
          )
      }

      val doTransform =
        sym.isRealMethod &&
        sym.isCase &&
        sym.name == nme.apply &&
        isClassTypeAccessible(tree)

      if (doTransform) {
        tree foreach {
          case i@Ident(_) =>
            enterReference(i.pos, i.symbol) // SI-5390 need to `enterReference` for `a` in `a.B()`
          case _ =>
        }
        toConstructor(tree.pos, tree.tpe)
      }
      else {
        ifNot
        tree
      }
    }

    private def transformApply(tree: Apply): Tree = tree match {
      case Apply(
        Select(qual, nme.filter | nme.withFilter),
        List(Function(
          List(ValDef(_, pname, tpt, _)),
          Match(_, CaseDef(pat1, _, _) :: _))))
        if ((pname startsWith nme.CHECK_IF_REFUTABLE_STRING) &&
            isIrrefutable(pat1, tpt.tpe) && (qual.tpe <:< tree.tpe)) =>

          transform(qual)

      case Apply(fn, args) =>
        // sensicality should be subsumed by the unreachability/exhaustivity/irrefutability
        // analyses in the pattern matcher
        if (!inPattern) {
          checkImplicitViewOptionApply(tree.pos, fn, args)
          checkSensible(tree.pos, fn, args)
        }
        currentApplication = tree
        tree
    }
    private def transformSelect(tree: Select): Tree = {
      val Select(qual, _) = tree
      val sym = tree.symbol

      checkUndesiredProperties(sym, tree.pos)
      checkDelayedInitSelect(qual, sym, tree.pos)

      if (!sym.exists)
        devWarning("Select node has NoSymbol! " + tree + " / " + tree.tpe)
      else if (sym.isLocalToThis)
        varianceValidator.checkForEscape(sym, currentClass)

      def checkSuper(mix: Name) =
        // term should have been eliminated by super accessors
        assert(!(qual.symbol.isTrait && sym.isTerm && mix == tpnme.EMPTY), (qual.symbol, sym, mix))

      transformCaseApply(tree,
        qual match {
          case Super(_, mix)  => checkSuper(mix)
          case _              =>
        }
      )
    }
    private def transformIf(tree: If): Tree = {
      val If(cond, thenpart, elsepart) = tree
      def unitIfEmpty(t: Tree): Tree =
        if (t == EmptyTree) Literal(Constant(())).setPos(tree.pos).setType(UnitTpe) else t

      cond.tpe match {
        case ConstantType(value) =>
          val res = if (value.booleanValue) thenpart else elsepart
          unitIfEmpty(res)
        case _ => tree
      }
    }

    // Warning about nullary methods returning Unit. TODO: move to lint
    private def checkNullaryMethodReturnType(sym: Symbol) = sym.tpe match {
      case NullaryMethodType(restpe) if restpe.typeSymbol == UnitClass =>
        // this may be the implementation of e.g. a generic method being parameterized
        // on Unit, in which case we had better let it slide.
        val isOk = (
             sym.isGetter
          || (sym.name containsName nme.DEFAULT_GETTER_STRING)
          || sym.allOverriddenSymbols.exists(over => !(over.tpe.resultType =:= sym.tpe.resultType))
        )
        if (!isOk)
          unit.warning(sym.pos, s"side-effecting nullary methods are discouraged: suggest defining as `def ${sym.name.decode}()` instead")
      case _ => ()
    }

    /* Convert a reference to a case factory of type `tpe` to a new of the class it produces. */
    def toConstructor(pos: Position, tpe: Type)(implicit ctx: Context): Tree = {
      val rtpe = tpe.finalResultType
      assert(rtpe.typeSymbol.is(Case), tpe)
      New(rtpe).withPos(pos).select(rtpe.typeSymbol.primaryConstructor)
    }
    private def isIrrefutable(pat: Tree, seltpe: Type): Boolean = pat match {
      case Apply(_, args) =>
        val clazz = pat.tpe.typeSymbol
        clazz == seltpe.typeSymbol &&
        clazz.isCaseClass &&
        (args corresponds clazz.primaryConstructor.tpe.asSeenFrom(seltpe, clazz).paramTypes)(isIrrefutable)
      case Typed(pat, tpt) =>
        seltpe <:< tpt.tpe
      case Ident(tpnme.WILDCARD) =>
        true
      case Bind(_, pat) =>
        isIrrefutable(pat, seltpe)
      case _ =>
        false
    }
    private def checkDelayedInitSelect(qual: Tree, sym: Symbol, pos: Position) = {
      def isLikelyUninitialized = (
           (sym.owner isSubClass DelayedInitClass)
        && !qual.tpe.isInstanceOf[ThisType]
        && sym.accessedOrSelf.isVal
      )
      if (settings.lint.value && isLikelyUninitialized)
        unit.warning(pos, s"Selecting ${sym} from ${sym.owner}, which extends scala.DelayedInit, is likely to yield an uninitialized value")
    }
    private def lessAccessible(otherSym: Symbol, memberSym: Symbol): Boolean = (
         (otherSym != NoSymbol)
      && !otherSym.isProtected
      && !otherSym.isTypeParameterOrSkolem
      && !otherSym.isExistentiallyBound
      && (otherSym isLessAccessibleThan memberSym)
      && (otherSym isLessAccessibleThan memberSym.enclClass)
    )
    private def lessAccessibleSymsInType(other: Type, memberSym: Symbol): List[Symbol] = {
      val extras = other match {
        case TypeRef(pre, _, args) =>
          // checking the prefix here gives us spurious errors on e.g. a private[process]
          // object which contains a type alias, which normalizes to a visible type.
          args filterNot (_ eq NoPrefix) flatMap (tp => lessAccessibleSymsInType(tp, memberSym))
        case _ =>
          Nil
      }
      if (lessAccessible(other.typeSymbol, memberSym)) other.typeSymbol :: extras
      else extras
    }
    private def warnLessAccessible(otherSym: Symbol, memberSym: Symbol) {
      val comparison = accessFlagsToString(memberSym) match {
        case ""   => ""
        case acc  => " is " + acc + " but"
      }
      val cannot =
        if (memberSym.isDeferred) "may be unable to provide a concrete implementation of"
        else "may be unable to override"

      unit.warning(memberSym.pos,
        "%s%s references %s %s.".format(
          memberSym.fullLocationString, comparison,
          accessFlagsToString(otherSym), otherSym
        ) + "\nClasses which cannot access %s %s %s.".format(
          otherSym.decodedName, cannot, memberSym.decodedName)
      )
    }

    /** Warn about situations where a method signature will include a type which
     *  has more restrictive access than the method itself.
     */
    private def checkAccessibilityOfReferencedTypes(tree: Tree) {
      val member = tree.symbol

      def checkAccessibilityOfType(tpe: Type) {
        val inaccessible = lessAccessibleSymsInType(tpe, member)
        // if the unnormalized type is accessible, that's good enough
        if (inaccessible.isEmpty) ()
        // or if the normalized type is, that's good too
        else if ((tpe ne tpe.normalize) && lessAccessibleSymsInType(tpe.dealiasWiden, member).isEmpty) ()
        // otherwise warn about the inaccessible syms in the unnormalized type
        else inaccessible foreach (sym => warnLessAccessible(sym, member))
      }

      // types of the value parameters
      mapParamss(member)(p => checkAccessibilityOfType(p.tpe))
      // upper bounds of type parameters
      member.typeParams.map(_.info.bounds.hi.widen) foreach checkAccessibilityOfType
    }

    private def checkByNameRightAssociativeDef(tree: DefDef) {
      tree match {
        case DefDef(_, name, _, params :: _, _, _) =>
          if (settings.lint && !treeInfo.isLeftAssoc(name.decodedName) && params.exists(p => isByName(p.symbol)))
            unit.warning(tree.pos,
              "by-name parameters will be evaluated eagerly when called as a right-associative infix operator. For more details, see SI-1980.")
        case _ =>
      }
    }
    override def transform(tree: Tree)(implicit ctx: Context): Tree = {
      //val savedLocalTyper = localTyper
      try {
        val sym = tree.symbol
        checkOverloadedRestrictions(ctx.owner)
            checkAllOverrides(ctx.owner)
            checkAnyValSubclass(ctx.owner)
            if (ctx.owner.isDerivedValueClass)
              ctx.owner.primaryConstructor.makeNotPrivateAfter(NoSymbol, thisTransformer) // SI-6601, must be done *after* pickler!
            tree


        // Apply RefChecks to annotations. Makes sure the annotations conform to
        // type bounds (bug #935), issues deprecation warnings for symbols used
        // inside annotations.
        // applyRefchecksToAnnotations(tree) ???
        var result: Tree = tree match {
          case tree: ValOrDefDef =>
            // move to lint:
            // if (settings.warnNullaryUnit)
            //  checkNullaryMethodReturnType(sym)
            // if (settings.warnInaccessible) {
            //  if (!sym.isConstructor && !sym.isEffectivelyFinal && !sym.isSynthetic)
            //    checkAccessibilityOfReferencedTypes(tree)
            // }
            // tree match {
            //  case dd: DefDef => checkByNameRightAssociativeDef(dd)
            //  case _          =>
            // }
            tree

          case Template(constr, parents, self, body) =>
            // localTyper = localTyper.atOwner(tree, currentOwner)
            checkOverloadedRestrictions(ctx.owner)
            checkAllOverrides(ctx.owner)
            checkAnyValSubclass(ctx.owner)
            if (ctx.owner.isDerivedValueClass)
              ctx.owner.primaryConstructor.makeNotPrivateAfter(NoSymbol, thisTransformer) // SI-6601, must be done *after* pickler!
            tree

          case tpt: TypeTree =>
            transform(tpt.original)
            tree

          case TypeApply(fn, args) =>
            checkBounds(tree, NoPrefix, NoSymbol, fn.tpe.typeParams, args map (_.tpe))
            transformCaseApply(tree, ())

          case x @ Apply(_, _)  =>
            transformApply(x)

          case x @ If(_, _, _)  =>
            transformIf(x)

          case New(tpt) =>
            enterReference(tree.pos, tpt.tpe.typeSymbol)
            tree

          case treeInfo.WildcardStarArg(_) if !isRepeatedParamArg(tree) =>
            unit.error(tree.pos, "no `: _*' annotation allowed here\n" +
              "(such annotations are only allowed in arguments to *-parameters)")
            tree

          case Ident(name) =>
            checkUndesiredProperties(sym, tree.pos)
            transformCaseApply(tree,
              if (name != nme.WILDCARD && name != tpnme.WILDCARD_STAR) {
                assert(sym != NoSymbol, "transformCaseApply: name = " + name.debugString + " tree = " + tree + " / " + tree.getClass) //debug
                enterReference(tree.pos, sym)
              }
            )

          case x @ Select(_, _) =>
            transformSelect(x)

          case UnApply(fun, args) =>
            transform(fun) // just make sure we enterReference for unapply symbols, note that super.transform(tree) would not transform(fun)
                           // transformTrees(args) // TODO: is this necessary? could there be forward references in the args??
                           // probably not, until we allow parameterised extractors
            tree


          case _ => tree
        }

        // skip refchecks in patterns....
        result = result match {
          case CaseDef(pat, guard, body) =>
            val pat1 = savingInPattern {
              inPattern = true
              transform(pat)
            }
            treeCopy.CaseDef(tree, pat1, transform(guard), transform(body))
          case LabelDef(_, _, _) if treeInfo.hasSynthCaseSymbol(result) =>
            savingInPattern {
              inPattern = true
              deriveLabelDef(result)(transform)
            }
          case Apply(fun, args) if fun.symbol.isLabel && treeInfo.isSynthCaseSymbol(fun.symbol) =>
            savingInPattern {
              // SI-7756 If we were in a translated pattern, we can now switch out of pattern mode, as the label apply signals
              //         that we are in the user-supplied code in the case body.
              //
              //         Relies on the translation of:
              //            (null: Any) match { case x: List[_] => x; x.reverse; case _ => }'
              //         to:
              //            <synthetic> val x2: List[_] = (x1.asInstanceOf[List[_]]: List[_]);
              //                  matchEnd4({ x2; x2.reverse}) // case body is an argument to a label apply.
              inPattern = false
              super.transform(result)
            }
          case ValDef(_, _, _, _) if treeInfo.hasSynthCaseSymbol(result) =>
            deriveValDef(result)(transform) // SI-7716 Don't refcheck the tpt of the synthetic val that holds the selector.
          case _ =>
            super.transform(result)
        }
        result match {
          case ClassDef(_, _, _, _)
             | TypeDef(_, _, _, _) =>
            if (result.symbol.isLocalToBlock || result.symbol.isTopLevel)
              varianceValidator.traverse(result)
          case tt @ TypeTree() if tt.original != null =>
            varianceValidator.traverse(tt.original) // See SI-7872
          case _ =>
        }

        checkUnexpandedMacro(result)

        result
      } catch {
        case ex: TypeError =>
          if (settings.debug) ex.printStackTrace()
          unit.error(tree.pos, ex.getMessage())
          tree
      } finally {
        localTyper = savedLocalTyper
        currentApplication = savedCurrentApplication
      }
    }
*/

