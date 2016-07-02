package dotty.tools.dotc
package core

import scala.language.implicitConversions
import scala.collection.{mutable, immutable}
import scala.annotation.switch
import Names._
import Symbols._
import Contexts._
import Decorators.StringDecorator
import util.NameTransformer
import scala.collection.breakOut

object StdNames {

/** Base strings from which synthetic names are derived. */

  abstract class DefinedNames[N <: Name] {
    protected implicit def fromString(s: String): N
    protected def fromName(name: Name): N = fromString(name.toString)

    private val kws = mutable.Set[N]()
    protected def kw(name: N) = { kws += name; name }

    final val keywords: collection.Set[N] = kws
  }

  abstract class ScalaNames[N <: Name] extends DefinedNames[N] {
    protected def encode(s: String): N = fromName(fromString(s).encode)

// Keywords, need to come first -----------------------

    final val ABSTRACTkw: N  = kw("abstract")
    final val CASEkw: N      = kw("case")
    final val CLASSkw: N     = kw("class")
    final val CATCHkw: N     = kw("catch")
    final val DEFkw: N       = kw("def")
    final val DOkw: N        = kw("do")
    final val ELSEkw: N      = kw("else")
    final val EXTENDSkw: N   = kw("extends")
    final val FALSEkw: N     = kw("false")
    final val FINALkw: N     = kw("final")
    final val FINALLYkw: N   = kw("finally")
    final val FORkw: N       = kw("for")
    final val FORSOMEkw: N   = kw("forSome")
    final val IFkw: N        = kw("if")
    final val IMPLICITkw: N  = kw("implicit")
    final val IMPORTkw: N    = kw("import")
    final val LAZYkw: N      = kw("lazy")
    final val MACROkw: N     = kw("macro")
    final val MATCHkw: N     = kw("match")
    final val NEWkw: N       = kw("new")
    final val NULLkw: N      = kw("null")
    final val OBJECTkw: N    = kw("object")
    final val OVERRIDEkw: N  = kw("override")
    final val PACKAGEkw: N   = kw("package")
    final val PRIVATEkw: N   = kw("private")
    final val PROTECTEDkw: N = kw("protected")
    final val RETURNkw: N    = kw("return")
    final val SEALEDkw: N    = kw("sealed")
    final val SUPERkw: N     = kw("super")
    final val THENkw: N      = kw("then")
    final val THISkw: N      = kw("this")
    final val THROWkw: N     = kw("throw")
    final val TRAITkw: N     = kw("trait")
    final val TRUEkw: N      = kw("true")
    final val TRYkw: N       = kw("try")
    final val TYPEkw: N      = kw("type")
    final val VALkw: N       = kw("val")
    final val VARkw: N       = kw("var")
    final val WITHkw: N      = kw("with")
    final val WHILEkw: N     = kw("while")
    final val YIELDkw: N     = kw("yield")
    final val DOTkw: N       = kw(".")
    final val USCOREkw: N    = kw("_")
    final val COLONkw: N     = kw(":")
    final val EQUALSkw: N    = kw("=")
    final val ARROWkw: N     = kw("=>")
    final val LARROWkw: N    = kw("<-")
    final val SUBTYPEkw: N   = kw("<:")
    final val VIEWBOUNDkw: N = kw("<%")
    final val SUPERTYPEkw: N = kw(">:")
    final val HASHkw: N      = kw("#")
    final val ATkw: N        = kw("@")

    val ANON_CLASS: N                 = "$anon"
    val ANON_FUN: N                   = "$anonfun"
    val BITMAP_PREFIX: N              = "bitmap$"
    val BITMAP_NORMAL: N              = BITMAP_PREFIX         // initialization bitmap for public/protected lazy vals
    val BITMAP_TRANSIENT: N           = BITMAP_PREFIX + "trans$"    // initialization bitmap for transient lazy vals
    val BITMAP_CHECKINIT: N           = BITMAP_PREFIX + "init$"      // initialization bitmap for checkinit values
    val BITMAP_CHECKINIT_TRANSIENT: N = BITMAP_PREFIX + "inittrans$" // initialization bitmap for transient checkinit values
    val DEFAULT_GETTER: N             = "$default$"
    val DEFAULT_GETTER_INIT: N        = NameTransformer.encode("<init>")
    val DO_WHILE_PREFIX: N            = "doWhile$"
    val EMPTY: N                      = ""
    val EMPTY_PACKAGE: N              = Names.EMPTY_PACKAGE.toString
    val EVIDENCE_PARAM_PREFIX: N      = "evidence$"
    val EXCEPTION_RESULT_PREFIX: N    = "exceptionResult"
    val EXPAND_SEPARATOR: N           = "$$"
    val IMPL_CLASS_SUFFIX: N          = "$class"
    val IMPORT: N                     = "<import>"
    val INTERPRETER_IMPORT_WRAPPER: N = "$iw"
    val INTERPRETER_LINE_PREFIX: N    = "line"
    val INTERPRETER_VAR_PREFIX: N     = "res"
    val INTERPRETER_WRAPPER_SUFFIX: N = "$object"
    val LOCALDUMMY_PREFIX: N          = "<local "       // owner of local blocks
    val MODULE_SUFFIX: N              = NameTransformer.MODULE_SUFFIX_STRING
    val AVOID_CLASH_SUFFIX: N         = "$_avoid_name_clash_$"
    val MODULE_VAR_SUFFIX: N          = "$module"
    val NAME_JOIN: N                  = NameTransformer.NAME_JOIN_STRING
    val USCORE_PARAM_PREFIX: N        = "_$"
    val OPS_PACKAGE: N                = "<special-ops>"
    val OVERLOADED: N                 = "<overloaded>"
    val PACKAGE: N                    = "package"
    val PACKAGE_CLS: N                = "package$"
    val PROTECTED_PREFIX: N           = "protected$"
    val PROTECTED_SET_PREFIX: N       = PROTECTED_PREFIX + "set"
    val ROOT: N                       = "<root>"
    val SHADOWED: N                   = "(shadowed)"  // tag to be used until we have proper name kinds
    val SINGLETON_SUFFIX: N           = ".type"
    val SPECIALIZED_SUFFIX: N         = "$sp"
    val SUPER_PREFIX: N               = "super$"
    val WHILE_PREFIX: N               = "while$"
    val DEFAULT_EXCEPTION_NAME: N     = "ex$"
    val INITIALIZER_PREFIX: N         = "initial$"
    val COMPANION_MODULE_METHOD: N    = "companion$module"
    val COMPANION_CLASS_METHOD: N     = "companion$class"
    val TRAIT_SETTER_SEPARATOR: N     = "$_setter_$"

    // value types (and AnyRef) are all used as terms as well
    // as (at least) arguments to the @specialize annotation.
    final val Boolean: N = "Boolean"
    final val Byte: N    = "Byte"
    final val Char: N    = "Char"
    final val Double: N  = "Double"
    final val Float: N   = "Float"
    final val Int: N     = "Int"
    final val Long: N    = "Long"
    final val Short: N   = "Short"
    final val Unit: N    = "Unit"

    final val ScalaValueNames: scala.List[N] =
      scala.List(Byte, Char, Short, Int, Long, Float, Double, Boolean, Unit)

    // some types whose companions we utilize
    final val AnyRef: N     = "AnyRef"
    final val Array: N      = "Array"
    final val List: N       = "List"
    final val Seq: N        = "Seq"
    final val Symbol: N     = "Symbol"
    final val ClassTag: N   = "ClassTag"
    final val classTag: N   = "classTag"
    final val WeakTypeTag: N = "WeakTypeTag"
    final val TypeTag : N   = "TypeTag"
    final val typeTag: N    = "typeTag"
    final val Expr: N       = "Expr"
    final val String: N     = "String"
    final val Annotation: N = "Annotation"

    // fictions we use as both types and terms
    final val ERROR: N    = "<error>"
    final val ERRORenc: N = encode("<error>")
    final val NO_NAME: N  = "<none>"  // formerly NOSYMBOL
    final val WILDCARD: N = "_"

// ----- Type names -----------------------------------------

    final val BYNAME_PARAM_CLASS: N             = "<byname>"
    final val EQUALS_PATTERN: N                 = "<equals>"
    final val LOCAL_CHILD: N                    = "<local child>"
    final val REPEATED_PARAM_CLASS: N           = "<repeated>"
    final val WILDCARD_STAR: N                  = "_*"
    final val REIFY_TREECREATOR_PREFIX: N       = "$treecreator"
    final val REIFY_TYPECREATOR_PREFIX: N       = "$typecreator"

    final val AbstractFunction: N    = "AbstractFunction"
    final val Any: N                 = "Any"
    final val AnyVal: N              = "AnyVal"
    final val ExprApi: N             = "ExprApi"
    final val Function: N            = "Function"
    final val Mirror: N              = "Mirror"
    final val Nothing: N             = "Nothing"
    final val Null: N                = "Null"
    final val Object: N              = "Object"
    final val PartialFunction: N     = "PartialFunction"
    final val PrefixType: N          = "PrefixType"
    final val Product: N             = "Product"
    final val Serializable: N        = "Serializable"
    final val Singleton: N           = "Singleton"
    final val Throwable: N           = "Throwable"
    final val Tuple: N               = "Tuple"

    final val ClassfileAnnotation: N = "ClassfileAnnotation"
    final val ClassManifest: N       = "ClassManifest"
    final val Enum: N                = "Enum"
    final val Group: N               = "Group"
    final val Tree: N                = "Tree"
    final val Type : N               = "Type"
    final val TypeTree: N            = "TypeTree"

    // Annotation simple names, used in Namer
    final val BeanPropertyAnnot: N = "BeanProperty"
    final val BooleanBeanPropertyAnnot: N = "BooleanBeanProperty"
    final val bridgeAnnot: N = "bridge"

    // Classfile Attributes
    final val AnnotationDefaultATTR: N      = "AnnotationDefault"
    final val BridgeATTR: N                 = "Bridge"
    final val ClassfileAnnotationATTR: N    = "RuntimeInvisibleAnnotations" // RetentionPolicy.CLASS. Currently not used (Apr 2009).
    final val CodeATTR: N                   = "Code"
    final val ConstantValueATTR: N          = "ConstantValue"
    final val DeprecatedATTR: N             = "Deprecated"
    final val ExceptionsATTR: N             = "Exceptions"
    final val InnerClassesATTR: N           = "InnerClasses"
    final val LineNumberTableATTR: N        = "LineNumberTable"
    final val LocalVariableTableATTR: N     = "LocalVariableTable"
    final val RuntimeAnnotationATTR: N      = "RuntimeVisibleAnnotations"   // RetentionPolicy.RUNTIME
    final val RuntimeParamAnnotationATTR: N = "RuntimeVisibleParameterAnnotations" // RetentionPolicy.RUNTIME (annotations on parameters)
    final val ScalaATTR: N                  = "Scala"
    final val ScalaSignatureATTR: N         = "ScalaSig"
    final val TASTYATTR: N                  = "TASTY"
    final val SignatureATTR: N              = "Signature"
    final val SourceFileATTR: N             = "SourceFile"
    final val SyntheticATTR: N              = "Synthetic"

// ----- Term names -----------------------------------------

    // Compiler-internal
    val ANYname: N                  = "<anyname>"
    val CONSTRUCTOR: N              = Names.CONSTRUCTOR.toString
    val DEFAULT_CASE: N             = "defaultCase$"
    val EVT2U: N                    = "evt2u$"
    val EQEQ_LOCAL_VAR: N           = "eqEqTemp$"
    val FAKE_LOCAL_THIS: N          = "this$"
    val LAZY_LOCAL: N               = "$lzy"
    val LAZY_LOCAL_INIT: N          = "$lzyINIT"
    val LAZY_FIELD_OFFSET: N        = "OFFSET$"
    val LAZY_SLOW_SUFFIX: N         = "$lzycompute"
    val LOCAL_SUFFIX: N             = "$$local"
    val UNIVERSE_BUILD_PREFIX: N    = "$u.build."
    val UNIVERSE_BUILD: N           = "$u.build"
    val UNIVERSE_PREFIX: N          = "$u."
    val UNIVERSE_SHORT: N           = "$u"
    val MIRROR_PREFIX: N            = "$m."
    val MIRROR_SHORT: N             = "$m"
    val MIRROR_UNTYPED: N           = "$m$untyped"
    val REIFY_FREE_PREFIX: N        = "free$"
    val REIFY_FREE_THIS_SUFFIX: N   = "$this"
    val REIFY_FREE_VALUE_SUFFIX: N  = "$value"
    val REIFY_SYMDEF_PREFIX: N      = "symdef$"
    val MODULE_INSTANCE_FIELD: N    = NameTransformer.MODULE_INSTANCE_NAME  // "MODULE$"
    val OUTER: N                    = "$outer"
    val OUTER_LOCAL: N              = "$outer "
    val OUTER_SYNTH: N              = "<outer>" // emitted by virtual pattern matcher, replaced by outer accessor in explicitouter
    val REFINE_CLASS: N             = "<refinement>"
    val ROOTPKG: N                  = "_root_"
    val SELECTOR_DUMMY: N           = "<unapply-selector>"
    val SELF: N                     = "$this"
    val SETTER_SUFFIX: N            = encode("_=")
    val SKOLEM: N                   = "<skolem>"
    val SPECIALIZED_INSTANCE: N     = "specInstance$"
    val THIS: N                     = "_$this"
    val TRAIT_CONSTRUCTOR: N        = "$init$"
    val U2EVT: N                    = "u2evt$"

    final val Nil: N                = "Nil"
    final val Predef: N             = "Predef"
    final val ScalaRunTime: N       = "ScalaRunTime"
    final val Some: N               = "Some"

    val x_0 : N  = "x$0"
    val x_1 : N  = "x$1"
    val x_2 : N  = "x$2"
    val x_3 : N  = "x$3"
    val x_4 : N  = "x$4"
    val x_5 : N  = "x$5"
    val x_6 : N  = "x$6"
    val x_7 : N  = "x$7"
    val x_8 : N  = "x$8"
    val x_9 : N  = "x$9"
    val _1 : N  = "_1"
    val _2 : N  = "_2"
    val _3 : N  = "_3"
    val _4 : N  = "_4"
    val _5 : N  = "_5"
    val _6 : N  = "_6"
    val _7 : N  = "_7"
    val _8 : N  = "_8"
    val _9 : N  = "_9"
    val _10 : N = "_10"
    val _11 : N = "_11"
    val _12 : N = "_12"
    val _13 : N = "_13"
    val _14 : N = "_14"
    val _15 : N = "_15"
    val _16 : N = "_16"
    val _17 : N = "_17"
    val _18 : N = "_18"
    val _19 : N = "_19"
    val _20 : N = "_20"
    val _21 : N = "_21"
    val _22 : N = "_22"

    val ??? = encode("???")

    val genericWrapArray: N     = "genericWrapArray"
    def wrapRefArray: N         = "wrapRefArray"
    def wrapXArray(clsName: Name): N = "wrap" + clsName + "Array"

    // Compiler utilized names

    val AnnotatedType: N        = "AnnotatedType"
    val AppliedTypeTree: N      = "AppliedTypeTree"
    val ArrayAnnotArg: N        = "ArrayAnnotArg"
    val Constant: N             = "Constant"
    val ConstantType: N         = "ConstantType"
    val ExistentialTypeTree: N  = "ExistentialTypeTree"
    val Flag : N                = "Flag"
    val Ident: N                = "Ident"
    val Import: N               = "Import"
    val Literal: N              = "Literal"
    val LiteralAnnotArg: N      = "LiteralAnnotArg"
    val Modifiers: N            = "Modifiers"
    val NestedAnnotArg: N       = "NestedAnnotArg"
    val NoFlags: N              = "NoFlags"
    val NoPrefix: N             = "NoPrefix"
    val NoSymbol: N             = "NoSymbol"
    val NoType: N               = "NoType"
    val Pair: N                 = "Pair"
    val Ref: N                  = "Ref"
    val RootPackage: N          = "RootPackage"
    val RootClass: N            = "RootClass"
    val Scala2: N               = "Scala2"
    val Select: N               = "Select"
    val StringContext: N        = "StringContext"
    val This: N                 = "This"
    val ThisType: N             = "ThisType"
    val Tuple2: N               = "Tuple2"
    val TYPE_ : N               = "TYPE"
    val TypeApply: N            = "TypeApply"
    val TypeRef: N              = "TypeRef"
    val UNIT : N                = "UNIT"
    val add_ : N                = "add"
    val annotation: N           = "annotation"
    val anyValClass: N          = "anyValClass"
    val append: N               = "append"
    val apply: N                = "apply"
    val applyDynamic: N         = "applyDynamic"
    val applyDynamicNamed: N    = "applyDynamicNamed"
    val applyOrElse: N          = "applyOrElse"
    val args : N                = "args"
    val argv : N                = "argv"
    val arrayClass: N           = "arrayClass"
    val arrayElementClass: N    = "arrayElementClass"
    val arrayValue: N           = "arrayValue"
    val array_apply : N         = "array_apply"
    val array_clone : N         = "array_clone"
    val array_length : N        = "array_length"
    val array_update : N        = "array_update"
    val arraycopy: N            = "arraycopy"
    val asTerm: N               = "asTerm"
    val asModule: N             = "asModule"
    val asMethod: N             = "asMethod"
    val asType: N               = "asType"
    val asClass: N              = "asClass"
    val asInstanceOf_ : N       = "asInstanceOf"
    val assert_ : N             = "assert"
    val assume_ : N             = "assume"
    val box: N                  = "box"
    val build : N               = "build"
    val bytes: N                = "bytes"
    val canEqual_ : N           = "canEqual"
    val checkInitialized: N     = "checkInitialized"
    val ClassManifestFactory: N = "ClassManifestFactory"
    val classOf: N              = "classOf"
    val clone_ : N              = "clone"
 //   val conforms : N             = "conforms" // Dotty deviation: no special treatment of conforms, so the occurrence of the name here would cause to unintended implicit shadowing. Should find a less common name for it in Predef.
    val copy: N                 = "copy"
    val currentMirror: N        = "currentMirror"
    val create: N               = "create"
    val definitions: N          = "definitions"
    val delayedInit: N          = "delayedInit"
    val delayedInitArg: N       = "delayedInit$body"
    val drop: N                 = "drop"
    val dummyApply: N           = "<dummy-apply>"
    val elem: N                 = "elem"
    val emptyValDef: N          = "emptyValDef"
    val ensureAccessible : N    = "ensureAccessible"
    val eq: N                   = "eq"
    val equalsNumChar : N       = "equalsNumChar"
    val equalsNumNum : N        = "equalsNumNum"
    val equalsNumObject : N     = "equalsNumObject"
    val equals_ : N             = "equals"
    val error: N                = "error"
    val eval: N                 = "eval"
    val eqAny: N                = "eqAny"
    val ex: N                   = "ex"
    val experimental: N         = "experimental"
    val f: N                    = "f"
    val false_ : N              = "false"
    val filter: N               = "filter"
    val finalize_ : N           = "finalize"
    val find_ : N               = "find"
    val flagsFromBits : N       = "flagsFromBits"
    val flatMap: N              = "flatMap"
    val foreach: N              = "foreach"
    val genericArrayOps: N      = "genericArrayOps"
    val get: N                  = "get"
    val getClass_ : N           = "getClass"
    val getOrElse: N            = "getOrElse"
    val hasNext: N              = "hasNext"
    val hashCode_ : N           = "hashCode"
    val hash_ : N               = "hash"
    val head: N                 = "head"
    val higherKinds: N          = "higherKinds"
    val identity: N             = "identity"
    val implicitly: N           = "implicitly"
    val in: N                   = "in"
    val info: N                 = "info"
    val inlinedEquals: N        = "inlinedEquals"
    val isArray: N              = "isArray"
    val isDefined: N            = "isDefined"
    val isDefinedAt: N          = "isDefinedAt"
    val isDefinedAtImpl: N      = "$isDefinedAt"
    val isEmpty: N              = "isEmpty"
    val isInstanceOf_ : N       = "isInstanceOf"
    val java: N                 = "java"
    val keepUnions: N           = "keepUnions"
    val key: N                  = "key"
    val lang: N                 = "lang"
    val length: N               = "length"
    val lengthCompare: N        = "lengthCompare"
    val liftedTree: N           = "liftedTree"
    val `macro` : N             = "macro"
    val macroThis : N           = "_this"
    val macroContext : N        = "c"
    val main: N                 = "main"
    val manifest: N             = "manifest"
    val ManifestFactory: N      = "ManifestFactory"
    val manifestToTypeTag: N    = "manifestToTypeTag"
    val map: N                  = "map"
    val materializeClassTag: N  = "materializeClassTag"
    val materializeWeakTypeTag: N = "materializeWeakTypeTag"
    val materializeTypeTag: N   = "materializeTypeTag"
    val mirror : N              = "mirror"
    val moduleClass : N         = "moduleClass"
    val name: N                 = "name"
    val ne: N                   = "ne"
    val newFreeTerm: N          = "newFreeTerm"
    val newFreeType: N          = "newFreeType"
    val newNestedSymbol: N      = "newNestedSymbol"
    val newScopeWith: N         = "newScopeWith"
    val next: N                 = "next"
    val nmeNewTermName: N       = "newTermName"
    val nmeNewTypeName: N       = "newTypeName"
    val noAutoTupling: N        = "noAutoTupling"
    val normalize: N            = "normalize"
    val notifyAll_ : N          = "notifyAll"
    val notify_ : N             = "notify"
    val null_ : N               = "null"
    val ofDim: N                = "ofDim"
    val origin: N               = "origin"
    val prefix : N              = "prefix"
    val productArity: N         = "productArity"
    val productElement: N       = "productElement"
    val productIterator: N      = "productIterator"
    val productPrefix: N        = "productPrefix"
    val readResolve: N          = "readResolve"
    val reflect : N             = "reflect"
    val reify : N               = "reify"
    val rootMirror : N          = "rootMirror"
    val runOrElse: N            = "runOrElse"
    val runtime: N              = "runtime"
    val runtimeClass: N         = "runtimeClass"
    val runtimeMirror: N        = "runtimeMirror"
    val sameElements: N         = "sameElements"
    val scala_ : N              = "scala"
    val selectDynamic: N        = "selectDynamic"
    val selectOverloadedMethod: N = "selectOverloadedMethod"
    val selectTerm: N           = "selectTerm"
    val selectType: N           = "selectType"
    val self: N                 = "self"
    val seqToArray: N           = "seqToArray"
    val setAccessible: N        = "setAccessible"
    val setAnnotations: N       = "setAnnotations"
    val setSymbol: N            = "setSymbol"
    val setType: N              = "setType"
    val setTypeSignature: N     = "setTypeSignature"
    val splice: N               = "splice"
    val staticClass : N         = "staticClass"
    val staticModule : N        = "staticModule"
    val staticPackage : N       = "staticPackage"
    val synchronized_ : N       = "synchronized"
    val tail: N                 = "tail"
    val `then` : N              = "then"
    val this_ : N               = "this"
    val thisPrefix : N          = "thisPrefix"
    val throw_ : N              = "throw"
    val toArray: N              = "toArray"
    val toList: N               = "toList"
    val toObjectArray : N       = "toObjectArray"
    val toSeq: N                = "toSeq"
    val toString_ : N           = "toString"
    val toTypeConstructor: N    = "toTypeConstructor"
    val tpe : N                 = "tpe"
    val tree : N                = "tree"
    val true_ : N               = "true"
    val typedProductIterator: N = "typedProductIterator"
    val typeTagToManifest: N    = "typeTagToManifest"
    val unapply: N              = "unapply"
    val unapplySeq: N           = "unapplySeq"
    val unbox: N                = "unbox"
    val universe: N             = "universe"
    val update: N               = "update"
    val updateDynamic: N        = "updateDynamic"
    val value: N                = "value"
    val valueOf : N             = "valueOf"
    val values : N              = "values"
    val view_ : N               = "view"
    val wait_ : N               = "wait"
    val withFilter: N           = "withFilter"
    val withFilterIfRefutable: N = "withFilterIfRefutable$"
    val wrap: N                 = "wrap"
    val zero: N                 = "zero"
    val zip: N                  = "zip"
    val nothingRuntimeClass: N  = "scala.runtime.Nothing$"
    val nullRuntimeClass: N     = "scala.runtime.Null$"

    val synthSwitch: N          = "$synthSwitch"

    val hkApply: N              = "$Apply"
    val hkArgPrefix: N          = "$hk"
    val hkLambdaPrefix: N       = "Lambda$"
    val hkArgPrefixHead: Char   = hkArgPrefix.head
    val hkArgPrefixLength: Int  = hkArgPrefix.length

    // unencoded operators
    object raw {
      final val AMP  : N  = "&"
      final val BANG : N  = "!"
      final val BAR  : N  = "|"
      final val DOLLAR: N = "$"
      final val GE: N     = ">="
      final val LE: N     = "<="
      final val MINUS: N  = "-"
      final val NE: N     = "!="
      final val PLUS : N  = "+"
      final val SLASH: N  = "/"
      final val STAR : N  = "*"
      final val TILDE: N  = "~"

      final val isUnary: Set[Name] = Set(MINUS, PLUS, TILDE, BANG)
    }

    object specializedTypeNames {
      final val Boolean: N = "Z"
      final val Byte: N    = "B"
      final val Char: N    = "C"
      final val Short: N   = "S"
      final val Int: N     = "I"
      final val Long: N    = "J"
      final val Float: N   = "F"
      final val Double: N  = "D"
      final val Void: N    = "V"
      final val Object: N  = "L"

      final val prefix: N = "$m"
      final val separator: N = "c"
      final val suffix: N = "$sp"
    }

    // value-conversion methods
    val toByte: N   = "toByte"
    val toShort: N  = "toShort"
    val toChar: N   = "toChar"
    val toInt: N    = "toInt"
    val toLong: N   = "toLong"
    val toFloat: N  = "toFloat"
    val toDouble: N = "toDouble"

    // primitive operation methods for structural types mostly
    // overlap with the above, but not for these two.
    val toCharacter: N = "toCharacter"
    val toInteger: N   = "toInteger"

    def newLazyValSlowComputeName(lzyValName: N) = lzyValName ++ LAZY_SLOW_SUFFIX

    // ASCII names for operators
    val ADD      = encode("+")
    val AND      = encode("&")
    val ASR      = encode(">>")
    val DIV      = encode("/")
    val EQ       = encode("==")
    val EQL      = encode("=")
    val GE       = encode(">=")
    val GT       = encode(">")
    val HASHHASH = encode("##")
    val LE       = encode("<=")
    val LSL      = encode("<<")
    val LSR      = encode(">>>")
    val LT       = encode("<")
    val MINUS    = encode("-")
    val MOD      = encode("%")
    val MUL      = encode("*")
    val NE       = encode("!=")
    val OR       = encode("|")
    val PLUS     = ADD    // technically redundant, but ADD looks funny with MINUS
    val SUB      = MINUS  // ... as does SUB with PLUS
    val XOR      = encode("^")
    val ZAND     = encode("&&")
    val ZOR      = encode("||")

    // unary operators
    val UNARY_PREFIX: N = "unary_"
    val UNARY_~ = encode("unary_~")
    val UNARY_+ = encode("unary_+")
    val UNARY_- = encode("unary_-")
    val UNARY_! = encode("unary_!")

    // Grouped here so Cleanup knows what tests to perform.
    val CommonOpNames   = Set[Name](OR, XOR, AND, EQ, NE)
    val ConversionNames = Set[Name](toByte, toChar, toDouble, toFloat, toInt, toLong, toShort)
    val BooleanOpNames  = Set[Name](ZOR, ZAND, UNARY_!) ++ CommonOpNames
    val NumberOpNames   = (
         Set[Name](ADD, SUB, MUL, DIV, MOD, LSL, LSR, ASR, LT, LE, GE, GT)
      ++ Set(UNARY_+, UNARY_-, UNARY_!)
      ++ ConversionNames
      ++ CommonOpNames
    )

    val add: N                    = "add"
    val complement: N             = "complement"
    val divide: N                 = "divide"
    val multiply: N               = "multiply"
    val negate: N                 = "negate"
    val positive: N               = "positive"
    val shiftLogicalRight: N      = "shiftLogicalRight"
    val shiftSignedLeft: N        = "shiftSignedLeft"
    val shiftSignedRight: N       = "shiftSignedRight"
    val subtract: N               = "subtract"
    val takeAnd: N                = "takeAnd"
    val takeConditionalAnd: N     = "takeConditionalAnd"
    val takeConditionalOr: N      = "takeConditionalOr"
    val takeModulo: N             = "takeModulo"
    val takeNot: N                = "takeNot"
    val takeOr: N                 = "takeOr"
    val takeXor: N                = "takeXor"
    val testEqual: N              = "testEqual"
    val testGreaterOrEqualThan: N = "testGreaterOrEqualThan"
    val testGreaterThan: N        = "testGreaterThan"
    val testLessOrEqualThan: N    = "testLessOrEqualThan"
    val testLessThan: N           = "testLessThan"
    val testNotEqual: N           = "testNotEqual"

    val isBoxedNumberOrBoolean: N = "isBoxedNumberOrBoolean"
    val isBoxedNumber: N = "isBoxedNumber"

    val reflPolyCacheName: N   = "reflPoly$Cache"
    val reflClassCacheName: N  = "reflClass$Cache"
    val reflParamsCacheName: N = "reflParams$Cache"
    val reflMethodCacheName: N = "reflMethod$Cache"
    val reflMethodName: N      = "reflMethod$Method"

    private val reflectionCacheNames = Set[N](
      reflPolyCacheName,
      reflClassCacheName,
      reflParamsCacheName,
      reflMethodCacheName,
      reflMethodName
    )

    def isReflectionCacheName(name: Name) = reflectionCacheNames exists (name startsWith _)
  }

  class ScalaTermNames extends ScalaNames[TermName] {
    protected implicit def fromString(s: String): TermName = termName(s)

    @switch def syntheticParamName(i: Int): TermName = i match {
      case 0  => x_0
      case 1  => x_1
      case 2  => x_2
      case 3  => x_3
      case 4  => x_4
      case 5  => x_5
      case 6  => x_6
      case 7  => x_7
      case 8  => x_8
      case 9  => x_9
      case _  => termName("x$" + i)
    }

    @switch def productAccessorName(j: Int): TermName = j match {
      case 1  => nme._1
      case 2  => nme._2
      case 3  => nme._3
      case 4  => nme._4
      case 5  => nme._5
      case 6  => nme._6
      case 7  => nme._7
      case 8  => nme._8
      case 9  => nme._9
      case 10 => nme._10
      case 11 => nme._11
      case 12 => nme._12
      case 13 => nme._13
      case 14 => nme._14
      case 15 => nme._15
      case 16 => nme._16
      case 17 => nme._17
      case 18 => nme._18
      case 19 => nme._19
      case 20 => nme._20
      case 21 => nme._21
      case 22 => nme._22
      case _  => termName("_" + j)
    }

    def syntheticParamNames(num: Int): List[TermName] =
      (0 until num).map(syntheticParamName)(breakOut)

    def localDummyName(clazz: Symbol)(implicit ctx: Context): TermName =
      LOCALDUMMY_PREFIX ++ clazz.name ++ ">"

    def newBitmapName(bitmapPrefix: TermName, n: Int): TermName = bitmapPrefix ++ n.toString

    def selectorName(n: Int): TermName = "_" + (n + 1)

    object primitive {
      val arrayApply: TermName  = "[]apply"
      val arrayUpdate: TermName = "[]update"
      val arrayLength: TermName = "[]length"
      val names: Set[Name] = Set(arrayApply, arrayUpdate, arrayLength)
    }

    def isPrimitiveName(name: Name) = primitive.names.contains(name)
  }

  class ScalaTypeNames extends ScalaNames[TypeName] {
    protected implicit def fromString(s: String): TypeName = typeName(s)

    @switch def syntheticTypeParamName(i: Int): TypeName = "T" + i

    def syntheticTypeParamNames(num: Int): List[TypeName] =
      (0 until num).map(syntheticTypeParamName)(breakOut)

    def hkLambda(vcs: List[Int]): TypeName = hkLambdaPrefix ++ vcs.map(varianceSuffix).mkString
    def hkArg(n: Int): TypeName = hkArgPrefix ++ n.toString

    def varianceSuffix(v: Int): Char = varianceSuffixes.charAt(v + 1)
    val varianceSuffixes = "NIP"

    final val Conforms = encode("<:<")
  }

  abstract class JavaNames[N <: Name] extends DefinedNames[N] {
    final val ABSTRACTkw: N     = kw("abstract")
    final val ASSERTkw: N       = kw("assert")
    final val BOOLEANkw: N      = kw("boolean")
    final val BREAKkw: N        = kw("break")
    final val BYTEkw: N         = kw("byte")
    final val CASEkw: N         = kw("case")
    final val CATCHkw: N        = kw("catch")
    final val CHARkw: N         = kw("char")
    final val CLASSkw: N        = kw("class")
    final val CONSTkw: N        = kw("const")
    final val CONTINUEkw: N     = kw("continue")
    final val DEFAULTkw: N      = kw("default")
    final val DOkw: N           = kw("do")
    final val DOUBLEkw: N       = kw("double")
    final val ELSEkw: N         = kw("else")
    final val ENUMkw: N         = kw("enum")
    final val EXTENDSkw: N      = kw("extends")
    final val FINALkw: N        = kw("final")
    final val FINALLYkw: N      = kw("finally")
    final val FLOATkw: N        = kw("float")
    final val FORkw: N          = kw("for")
    final val IFkw: N           = kw("if")
    final val GOTOkw: N         = kw("goto")
    final val IMPLEMENTSkw: N   = kw("implements")
    final val IMPORTkw: N       = kw("import")
    final val INSTANCEOFkw: N   = kw("instanceof")
    final val INTkw: N          = kw("int")
    final val INTERFACEkw: N    = kw("interface")
    final val LONGkw: N         = kw("long")
    final val NATIVEkw: N       = kw("native")
    final val NEWkw: N          = kw("new")
    final val PACKAGEkw: N      = kw("package")
    final val PRIVATEkw: N      = kw("private")
    final val PROTECTEDkw: N    = kw("protected")
    final val PUBLICkw: N       = kw("public")
    final val RETURNkw: N       = kw("return")
    final val SHORTkw: N        = kw("short")
    final val STATICkw: N       = kw("static")
    final val STRICTFPkw: N     = kw("strictfp")
    final val SUPERkw: N        = kw("super")
    final val SWITCHkw: N       = kw("switch")
    final val SYNCHRONIZEDkw: N = kw("synchronized")
    final val THISkw: N         = kw("this")
    final val THROWkw: N        = kw("throw")
    final val THROWSkw: N       = kw("throws")
    final val TRANSIENTkw: N    = kw("transient")
    final val TRYkw: N          = kw("try")
    final val VOIDkw: N         = kw("void")
    final val VOLATILEkw: N     = kw("volatile")
    final val WHILEkw: N        = kw("while")

    final val BoxedBoolean: N       = "java.lang.Boolean"
    final val BoxedByte: N          = "java.lang.Byte"
    final val BoxedCharacter: N     = "java.lang.Character"
    final val BoxedDouble: N        = "java.lang.Double"
    final val BoxedFloat: N         = "java.lang.Float"
    final val BoxedInteger: N       = "java.lang.Integer"
    final val BoxedLong: N          = "java.lang.Long"
    final val BoxedNumber: N        = "java.lang.Number"
    final val BoxedShort: N         = "java.lang.Short"
    final val Class: N              = "java.lang.Class"
    final val IOOBException: N      = "java.lang.IndexOutOfBoundsException"
    final val InvTargetException: N = "java.lang.reflect.InvocationTargetException"
    final val MethodAsObject: N     = "java.lang.reflect.Method"
    final val NPException: N        = "java.lang.NullPointerException"
    final val Object: N             = "java.lang.Object"
    final val String: N             = "java.lang.String"
    final val Throwable: N          = "java.lang.Throwable"

    final val ForName: N          = "forName"
    final val GetCause: N         = "getCause"
    final val GetClass: N         = "getClass"
    final val GetClassLoader: N   = "getClassLoader"
    final val GetComponentType: N = "getComponentType"
    final val GetMethod: N        = "getMethod"
    final val Invoke: N           = "invoke"
    final val JavaLang: N         = "java.lang"

    final val BeanProperty: N        = "scala.beans.BeanProperty"
    final val BooleanBeanProperty: N = "scala.beans.BooleanBeanProperty"
    final val JavaSerializable: N    = "java.io.Serializable"
   }

  class JavaTermNames extends JavaNames[TermName] {
    protected def fromString(s: String): TermName = termName(s)
  }
  class JavaTypeNames extends JavaNames[TypeName] {
    protected def fromString(s: String): TypeName = typeName(s)
  }

  val nme = new ScalaTermNames
  val tpnme = new ScalaTypeNames
  val jnme = new JavaTermNames
  val jtpnme = new JavaTypeNames

}
