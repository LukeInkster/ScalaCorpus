package dotty.tools.backend.jvm

import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc
import dotty.tools.dotc.backend.jvm.DottyPrimitives
import dotty.tools.dotc.core.Flags.FlagSet
import dotty.tools.dotc.transform.Erasure
import dotty.tools.dotc.transform.SymUtils._
import java.io.{File => JFile}

import scala.collection.generic.Clearable
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.reflect.internal.util.WeakHashSet
import scala.reflect.io.{AbstractFile, Directory, PlainDirectory}
import scala.tools.asm.{AnnotationVisitor, ClassVisitor, FieldVisitor, MethodVisitor}
import scala.tools.nsc.backend.jvm.{BCodeHelpers, BackendInterface}
import dotty.tools.dotc.core._
import Periods._
import SymDenotations._
import Contexts._
import Types._
import Symbols._
import Denotations._
import Phases._
import java.lang.AssertionError

import dotty.tools.dotc.util.{DotClass, Positions}
import Decorators._
import tpd._

import scala.tools.asm
import NameOps._
import StdNames.nme
import NameOps._
import dotty.tools.dotc.core

class DottyBackendInterface(outputDirectory: AbstractFile)(implicit ctx: Context) extends BackendInterface{
  type Symbol          = Symbols.Symbol
  type Type            = Types.Type
  type Tree            = tpd.Tree
  type CompilationUnit = dotc.CompilationUnit
  type Constant        = Constants.Constant
  type Literal         = tpd.Literal
  type Position        = Positions.Position
  type Name            = Names.Name
  type ClassDef        = tpd.TypeDef
  type TypeDef         = tpd.TypeDef
  type Apply           = tpd.Apply
  type TypeApply       = tpd.TypeApply
  type Try             = tpd.Try
  type Assign          = tpd.Assign
  type Ident           = tpd.Ident
  type If              = tpd.If
  type ValDef          = tpd.ValDef
  type Throw           = tpd.Apply
  type Return          = tpd.Return
  type Block           = tpd.Block
  type Typed           = tpd.Typed
  type Match           = tpd.Match
  type This            = tpd.This
  type CaseDef         = tpd.CaseDef
  type Alternative     = tpd.Alternative
  type DefDef          = tpd.DefDef
  type Template        = tpd.Template
  type Select          = tpd.Tree // Actually tpd.Select || tpd.Ident
  type Bind            = tpd.Bind
  type New             = tpd.New
  type Super           = tpd.Super
  type Modifiers       = tpd.Modifiers
  type Annotation      = Annotations.Annotation
  type ArrayValue      = tpd.JavaSeqLiteral
  type ApplyDynamic    = Null
  type ModuleDef       = Null
  type LabelDef        = tpd.DefDef
  type Closure         = tpd.Closure

  val NoSymbol = Symbols.NoSymbol
  val NoPosition: Position = Positions.NoPosition
  val EmptyTree: Tree = tpd.EmptyTree


  val UnitTag: ConstantTag = Constants.UnitTag
  val IntTag: ConstantTag = Constants.IntTag
  val FloatTag: ConstantTag = Constants.FloatTag
  val NullTag: ConstantTag = Constants.NullTag
  val BooleanTag: ConstantTag = Constants.BooleanTag
  val ByteTag: ConstantTag = Constants.ByteTag
  val ShortTag: ConstantTag = Constants.ShortTag
  val CharTag: ConstantTag = Constants.CharTag
  val DoubleTag: ConstantTag = Constants.DoubleTag
  val LongTag: ConstantTag = Constants.LongTag
  val StringTag: ConstantTag = Constants.StringTag
  val ClazzTag: ConstantTag = Constants.ClazzTag
  val EnumTag: ConstantTag = Constants.EnumTag

  val nme_This: Name = StdNames.nme.This
  val nme_EMPTY_PACKAGE_NAME: Name = StdNames.nme.EMPTY_PACKAGE
  val nme_CONSTRUCTOR: Name = StdNames.nme.CONSTRUCTOR
  val nme_WILDCARD: Name = StdNames.nme.WILDCARD
  val nme_THIS: Name = StdNames.nme.THIS
  val nme_PACKAGE: Name = StdNames.nme.PACKAGE
  val nme_EQEQ_LOCAL_VAR: Name = StdNames.nme.EQEQ_LOCAL_VAR

   // require LambdaMetafactory: scalac uses getClassIfDefined, but we need those always.
  override lazy val LambdaMetaFactory = ctx.requiredClass("java.lang.invoke.LambdaMetafactory")
  override lazy val MethodHandle      = ctx.requiredClass("java.lang.invoke.MethodHandle")

  val nme_valueOf: Name = StdNames.nme.valueOf
  val nme_apply = StdNames.nme.apply
  val NothingClass: Symbol = defn.NothingClass
  val NullClass: Symbol = defn.NullClass
  val ObjectClass: Symbol = defn.ObjectClass
  val Object_Type: Type = defn.ObjectType
  val Throwable_Type: Type = defn.ThrowableType
  val Object_isInstanceOf: Symbol = defn.Any_isInstanceOf
  val Object_asInstanceOf: Symbol = defn.Any_asInstanceOf
  val Object_equals: Symbol = defn.Any_equals
  val ArrayClass: Symbol = defn.ArrayClass
  val UnitClass: Symbol = defn.UnitClass
  val BooleanClass: Symbol = defn.BooleanClass
  val CharClass: Symbol = defn.CharClass
  val ShortClass: Symbol = defn.ShortClass
  val ClassClass: Symbol = defn.ClassClass
  val ByteClass: Symbol = defn.ByteClass
  val IntClass: Symbol = defn.IntClass
  val LongClass: Symbol = defn.LongClass
  val FloatClass: Symbol = defn.FloatClass
  val DoubleClass: Symbol = defn.DoubleClass
  def isArrayClone(tree: Tree) = tree match {
    case Select(qual, StdNames.nme.clone_) if qual.tpe.widen.isInstanceOf[JavaArrayType] => true
    case _ => false
  }

  val hashMethodSym: Symbol = NoSymbol // used to dispatch ## on primitives to ScalaRuntime.hash. Should be implemented by a miniphase
  val externalEqualsNumNum: Symbol = defn.BoxesRunTimeModule.requiredMethod(nme.equalsNumNum)
  val externalEqualsNumChar: Symbol = NoSymbol // ctx.requiredMethod(BoxesRunTimeTypeRef, nme.equalsNumChar) // this method is private
  val externalEqualsNumObject: Symbol = defn.BoxesRunTimeModule.requiredMethod(nme.equalsNumObject)
  val externalEquals: Symbol = defn.BoxesRunTimeClass.info.decl(nme.equals_).suchThat(toDenot(_).info.firstParamTypes.size == 2).symbol
  val MaxFunctionArity: Int = Definitions.MaxFunctionArity
  val FunctionClass: Array[Symbol] = defn.FunctionClassPerRun()
  val AbstractFunctionClass: Array[Symbol] = defn.AbstractFunctionClassPerRun()
  val PartialFunctionClass: Symbol = defn.PartialFunctionClass
  val AbstractPartialFunctionClass: Symbol = defn.AbstractPartialFunctionClass
  val String_valueOf: Symbol = defn.String_valueOf_Object
  lazy val Predef_classOf: Symbol = defn.ScalaPredefModule.requiredMethod(nme.classOf)

  lazy val AnnotationRetentionAttr = ctx.requiredClass("java.lang.annotation.Retention")
  lazy val AnnotationRetentionSourceAttr = ctx.requiredClass("java.lang.annotation.RetentionPolicy").linkedClass.requiredValue("SOURCE")
  lazy val AnnotationRetentionClassAttr = ctx.requiredClass("java.lang.annotation.RetentionPolicy").linkedClass.requiredValue("CLASS")
  lazy val AnnotationRetentionRuntimeAttr = ctx.requiredClass("java.lang.annotation.RetentionPolicy").linkedClass.requiredValue("RUNTIME")
  lazy val JavaAnnotationClass = ctx.requiredClass("java.lang.annotation.Annotation")

  def boxMethods: Map[Symbol, Symbol] = defn.ScalaValueClasses().map{x => // @darkdimius Are you sure this should be a def?
    (x, Erasure.Boxing.boxMethod(x.asClass))
  }.toMap
  def unboxMethods: Map[Symbol, Symbol] = defn.ScalaValueClasses().map(x => (x, Erasure.Boxing.unboxMethod(x.asClass))).toMap

  override def isSyntheticArrayConstructor(s: Symbol) = {
    s eq defn.newArrayMethod
  }

  def isBox(sym: Symbol): Boolean = Erasure.Boxing.isBox(sym)
  def isUnbox(sym: Symbol): Boolean = Erasure.Boxing.isUnbox(sym)

  val primitives: Primitives = new Primitives {
    val primitives = new DottyPrimitives(ctx)
    def getPrimitive(app: Apply, reciever: Type): Int = primitives.getPrimitive(app, reciever)

    def getPrimitive(sym: Symbol): Int = primitives.getPrimitive(sym)

    def isPrimitive(fun: Tree): Boolean = primitives.isPrimitive(fun)
  }
  implicit val TypeDefTag: ClassTag[TypeDef] = ClassTag[TypeDef](classOf[TypeDef])
  implicit val ApplyTag: ClassTag[Apply] = ClassTag[Apply](classOf[Apply])
  implicit val SelectTag: ClassTag[Select] = ClassTag[Select](classOf[Select])
  implicit val TypeApplyTag: ClassTag[TypeApply] = ClassTag[TypeApply](classOf[TypeApply])
  implicit val ClassDefTag: ClassTag[ClassDef] = ClassTag[TypeDef](classOf[TypeDef])
  implicit val TryTag: ClassTag[Try] = ClassTag[Try](classOf[Try])
  implicit val AssignTag: ClassTag[Assign] = ClassTag[Assign](classOf[Assign])
  implicit val IdentTag: ClassTag[Ident] = ClassTag[Ident](classOf[Ident])
  implicit val IfTag: ClassTag[If] = ClassTag[If](classOf[If])
  implicit val LabelDefTag: ClassTag[LabelDef] = ClassTag[LabelDef](classOf[LabelDef])
  implicit val ValDefTag: ClassTag[ValDef] = ClassTag[ValDef](classOf[ValDef])
  implicit val ThrowTag: ClassTag[Throw] = ClassTag[Throw](classOf[Throw])
  implicit val ReturnTag: ClassTag[Return] = ClassTag[Return](classOf[Return])
  implicit val LiteralTag: ClassTag[Literal] = ClassTag[Literal](classOf[Literal])
  implicit val BlockTag: ClassTag[Block] = ClassTag[Block](classOf[Block])
  implicit val TypedTag: ClassTag[Typed] = ClassTag[Typed](classOf[Typed])
  implicit val ArrayValueTag: ClassTag[ArrayValue] = ClassTag[ArrayValue](classOf[ArrayValue])
  implicit val MatchTag: ClassTag[Match] = ClassTag[Match](classOf[Match])
  implicit val CaseDefTag: ClassTag[CaseDef] = ClassTag[CaseDef](classOf[CaseDef])
  implicit val ThisTag: ClassTag[This] = ClassTag[This](classOf[This])
  implicit val AlternativeTag: ClassTag[Alternative] = ClassTag[Alternative](classOf[Alternative])
  implicit val DefDefTag: ClassTag[DefDef] = ClassTag[DefDef](classOf[DefDef])
  implicit val ModuleDefTag: ClassTag[ModuleDef] = ClassTag[ModuleDef](classOf[ModuleDef])
  implicit val NameTag: ClassTag[Name] = ClassTag[Name](classOf[Name])
  implicit val TemplateTag: ClassTag[Template] = ClassTag[Template](classOf[Template])
  implicit val BindTag: ClassTag[Bind] = ClassTag[Bind](classOf[Bind])
  implicit val NewTag: ClassTag[New] = ClassTag[New](classOf[New])
  implicit val ApplyDynamicTag: ClassTag[ApplyDynamic] = ClassTag[ApplyDynamic](classOf[ApplyDynamic])
  implicit val SuperTag: ClassTag[Super] = ClassTag[Super](classOf[Super])
  implicit val ConstantClassTag: ClassTag[Constant] = ClassTag[Constant](classOf[Constant])
  implicit val ClosureTag: ClassTag[Closure] = ClassTag[Closure](classOf[Closure])

  /* dont emit any annotations for now*/
  def isRuntimeVisible(annot: Annotation): Boolean = {
    annot.atp.typeSymbol.getAnnotation(AnnotationRetentionAttr) match {
      case Some(retentionAnnot) =>
        retentionAnnot.tree.find(_.symbol == AnnotationRetentionRuntimeAttr).isDefined
      case _ =>
        // SI-8926: if the annotation class symbol doesn't have a @RetentionPolicy annotation, the
        // annotation is emitted with visibility `RUNTIME`
        // dotty bug: #389
        true
    }
  }

  def shouldEmitAnnotation(annot: Annotation): Boolean = {
    annot.symbol.isJavaDefined &&
      retentionPolicyOf(annot) != AnnotationRetentionSourceAttr &&
      annot.args.isEmpty
  }

  private def retentionPolicyOf(annot: Annotation): Symbol =
    annot.atp.typeSymbol.getAnnotation(AnnotationRetentionAttr).
      flatMap(_.argument(0).map(_.symbol)).getOrElse(AnnotationRetentionClassAttr)

  private def emitArgument(av:   AnnotationVisitor,
                           name: String,
                           arg:  Tree, bcodeStore: BCodeHelpers)(innerClasesStore: bcodeStore.BCInnerClassGen): Unit = {
    (arg: @unchecked) match {

      case Literal(const @ Constant(_)) =>
        const.tag match {
          case BooleanTag | ByteTag | ShortTag | CharTag | IntTag | LongTag | FloatTag | DoubleTag => av.visit(name, const.value)
          case StringTag =>
            assert(const.value != null, const) // TODO this invariant isn't documented in `case class Constant`
            av.visit(name, const.stringValue) // `stringValue` special-cases null, but that execution path isn't exercised for a const with StringTag
          case ClazzTag => av.visit(name, const.typeValue.toTypeKind(bcodeStore)(innerClasesStore).toASMType)
          case EnumTag =>
            val edesc = innerClasesStore.typeDescriptor(const.tpe.asInstanceOf[bcodeStore.int.Type]) // the class descriptor of the enumeration class.
            val evalue = const.symbolValue.name.toString // value the actual enumeration value.
            av.visitEnum(name, edesc, evalue)
        }
      case t: TypeApply if (t.fun.symbol == Predef_classOf) =>
        av.visit(name, t.args.head.tpe.classSymbol.denot.info.toTypeKind(bcodeStore)(innerClasesStore).toASMType)
      case t: tpd.Select =>
        if (t.symbol.denot.is(Flags.Enum)) {
          val edesc = innerClasesStore.typeDescriptor(t.tpe.asInstanceOf[bcodeStore.int.Type]) // the class descriptor of the enumeration class.
          val evalue = t.symbol.name.toString // value the actual enumeration value.
          av.visitEnum(name, edesc, evalue)
        } else {
          assert(toDenot(t.symbol).name.toTermName.defaultGetterIndex >= 0) // this should be default getter. do not emmit.
        }
      case t: SeqLiteral =>
        val arrAnnotV: AnnotationVisitor = av.visitArray(name)
        for(arg <- t.elems) { emitArgument(arrAnnotV, null, arg, bcodeStore)(innerClasesStore) }
        arrAnnotV.visitEnd()

      case Apply(fun, args) if (fun.symbol == defn.ArrayClass.primaryConstructor ||
        (toDenot(fun.symbol).owner == defn.ArrayClass.linkedClass && fun.symbol.name == nme_apply)) =>
        val arrAnnotV: AnnotationVisitor = av.visitArray(name)

        var actualArgs = if (fun.tpe.isInstanceOf[ImplicitMethodType]) {
          // generic array method, need to get implicit argument out of the way
          fun.asInstanceOf[Apply].args
        } else args

        val flatArgs = actualArgs.flatMap {
          case t: tpd.SeqLiteral => t.elems
          case e => List(e)
        }
        for(arg <- flatArgs) { emitArgument(arrAnnotV, null, arg, bcodeStore)(innerClasesStore) }
        arrAnnotV.visitEnd()
/*
      case sb @ ScalaSigBytes(bytes) =>
        // see http://www.scala-lang.org/sid/10 (Storage of pickled Scala signatures in class files)
        // also JVMS Sec. 4.7.16.1 The element_value structure and JVMS Sec. 4.4.7 The CONSTANT_Utf8_info Structure.
        if (sb.fitsInOneString) {
          av.visit(name, BCodeAsmCommon.strEncode(sb))
        } else {
          val arrAnnotV: asm.AnnotationVisitor = av.visitArray(name)
          for(arg <- BCodeAsmCommon.arrEncode(sb)) { arrAnnotV.visit(name, arg) }
          arrAnnotV.visitEnd()
        }          // for the lazy val in ScalaSigBytes to be GC'ed, the invoker of emitAnnotations() should hold the ScalaSigBytes in a method-local var that doesn't escape.
*/
      case t @ Apply(constr, args) if t.tpe.derivesFrom(JavaAnnotationClass) =>
        val typ = t.tpe.classSymbol.denot.info
        val assocs = assocsFromApply(t)
        val desc = innerClasesStore.typeDescriptor(typ.asInstanceOf[bcodeStore.int.Type]) // the class descriptor of the nested annotation class
        val nestedVisitor = av.visitAnnotation(name, desc)
        emitAssocs(nestedVisitor, assocs, bcodeStore)(innerClasesStore)
    }
  }

  override def emitAnnotations(cw: asm.ClassVisitor, annotations: List[Annotation], bcodeStore: BCodeHelpers)
                              (innerClasesStore: bcodeStore.BCInnerClassGen) = {
    for(annot <- annotations; if shouldEmitAnnotation(annot)) {
      val typ = annot.atp
      val assocs = annot.assocs
      val av = cw.visitAnnotation(innerClasesStore.typeDescriptor(typ.asInstanceOf[bcodeStore.int.Type]), isRuntimeVisible(annot))
      emitAssocs(av, assocs, bcodeStore)(innerClasesStore)
    }
  }

  private def emitAssocs(av: asm.AnnotationVisitor, assocs: List[(Name, Object)], bcodeStore: BCodeHelpers)
                        (innerClasesStore: bcodeStore.BCInnerClassGen) = {
    for ((name, value) <- assocs)
      emitArgument(av, name.toString, value.asInstanceOf[Tree], bcodeStore)(innerClasesStore)
    av.visitEnd()
  }

  override def emitAnnotations(mw: asm.MethodVisitor, annotations: List[Annotation], bcodeStore: BCodeHelpers)
                              (innerClasesStore: bcodeStore.BCInnerClassGen) = {
    for(annot <- annotations; if shouldEmitAnnotation(annot)) {
      val typ = annot.atp
      val assocs = annot.assocs
      val av = mw.visitAnnotation(innerClasesStore.typeDescriptor(typ.asInstanceOf[bcodeStore.int.Type]), isRuntimeVisible(annot))
      emitAssocs(av, assocs, bcodeStore)(innerClasesStore)
    }
  }

  override def emitAnnotations(fw: asm.FieldVisitor, annotations: List[Annotation], bcodeStore: BCodeHelpers)
                              (innerClasesStore: bcodeStore.BCInnerClassGen) = {
    for(annot <- annotations; if shouldEmitAnnotation(annot)) {
      val typ = annot.atp
      val assocs = annot.assocs
      val av = fw.visitAnnotation(innerClasesStore.typeDescriptor(typ.asInstanceOf[bcodeStore.int.Type]), isRuntimeVisible(annot))
      emitAssocs(av, assocs, bcodeStore)(innerClasesStore)
    }
  }

  override def emitParamAnnotations(jmethod: asm.MethodVisitor, pannotss: List[List[Annotation]], bcodeStore: BCodeHelpers)
                                   (innerClasesStore: bcodeStore.BCInnerClassGen): Unit = {
    val annotationss = pannotss map (_ filter shouldEmitAnnotation)
    if (annotationss forall (_.isEmpty)) return
    for ((annots, idx) <- annotationss.zipWithIndex;
         annot <- annots) {
      val typ = annot.atp
      val assocs = annot.assocs
      val pannVisitor: asm.AnnotationVisitor = jmethod.visitParameterAnnotation(idx, innerClasesStore.typeDescriptor(typ.asInstanceOf[bcodeStore.int.Type]), isRuntimeVisible(annot))
      emitAssocs(pannVisitor, assocs, bcodeStore)(innerClasesStore)
    }
  }

  def getAnnotPickle(jclassName: String, sym: Symbol): Option[Annotation] = None


  def getRequiredClass(fullname: String): Symbol = ctx.requiredClass(fullname.toTermName)

  def getClassIfDefined(fullname: String): Symbol = NoSymbol // used only for android. todo: implement

  private def erasureString(clazz: Class[_]): String = {
    if (clazz.isArray) "Array[" + erasureString(clazz.getComponentType) + "]"
    else clazz.getName
  }

  def requiredClass[T](implicit evidence: ClassTag[T]): Symbol = {
    ctx.requiredClass(erasureString(evidence.runtimeClass).toTermName)
  }

  def requiredModule[T](implicit evidence: ClassTag[T]): Symbol = {
    val moduleName = erasureString(evidence.runtimeClass)
    val className = if (moduleName.endsWith("$")) moduleName.dropRight(1)  else moduleName
    ctx.requiredModule(className.toTermName)
  }


  def debuglog(msg: => String): Unit = ctx.debuglog(msg)
  def informProgress(msg: String): Unit = ctx.informProgress(msg)
  def log(msg: => String): Unit = ctx.log(msg)
  def error(pos: Position, msg: String): Unit = ctx.error(msg, pos)
  def warning(pos: Position, msg: String): Unit = ctx.warning(msg, pos)
  def abort(msg: String): Nothing = {
    ctx.error(msg)
    throw new RuntimeException(msg)
  }

  def emitAsmp: Option[String] = None

  def shouldEmitJumpAfterLabels = true

  def dumpClasses: Option[String] =
    if (ctx.settings.Ydumpclasses.isDefault) None
    else Some(ctx.settings.Ydumpclasses.value)

  def mainClass: Option[String] =
    if (ctx.settings.mainClass.isDefault) None
    else Some(ctx.settings.mainClass.value)
  def setMainClass(name: String): Unit = ctx.settings.mainClass.update(name)


  def noForwarders: Boolean = ctx.settings.noForwarders.value
  def debuglevel: Int = 3 // 0 -> no debug info; 1-> filename; 2-> lines; 3-> varnames
  def settings_debug: Boolean = ctx.settings.debug.value
  def targetPlatform: String = ctx.settings.target.value

  val perRunCaches: Caches = new Caches {
    def newAnyRefMap[K <: AnyRef, V](): mutable.AnyRefMap[K, V] = new mutable.AnyRefMap[K, V]()
    def newWeakMap[K, V](): mutable.WeakHashMap[K, V] = new mutable.WeakHashMap[K, V]()
    def recordCache[T <: Clearable](cache: T): T = cache
    def newWeakSet[K <: AnyRef](): WeakHashSet[K] = new WeakHashSet[K]()
    def newMap[K, V](): mutable.HashMap[K, V] = new mutable.HashMap[K, V]()
    def newSet[K](): mutable.Set[K] = new mutable.HashSet[K]
  }



  val MODULE_INSTANCE_FIELD: String = nme.MODULE_INSTANCE_FIELD.toString

  def internalNameString(offset: Int, length: Int): String = new String(Names.chrs, offset, length)

  def newTermName(prefix: String): Name = prefix.toTermName

  val Flag_SYNTHETIC: Flags = Flags.Synthetic.bits
  val Flag_METHOD: Flags = Flags.Method.bits
  val ExcludedForwarderFlags: Flags = {
      Flags.Specialized | Flags.Lifted | Flags.Protected | Flags.JavaStatic |
     Flags.ExpandedName | Flags.Bridge | Flags.VBridge | Flags.Private | Flags.Macro
  }.bits


  def isQualifierSafeToElide(qual: Tree): Boolean = tpd.isIdempotentExpr(qual)
  def desugarIdent(i: Ident): Option[tpd.Select] = {
    i.tpe match {
      case TermRef(prefix: TermRef, name) =>
        Some(tpd.ref(prefix).select(i.symbol))
      case TermRef(prefix: ThisType, name) =>
        Some(tpd.This(prefix.cls).select(i.symbol))
      case TermRef(NoPrefix, name) =>
        if (i.symbol is Flags.Method) Some(This(i.symbol.topLevelClass).select(i.symbol)) // workaround #342 todo: remove after fixed
        else None
      case _ => None
    }
  }
  def getLabelDefOwners(tree: Tree): Map[Tree, List[LabelDef]] = {
    // for each rhs of a defdef returns LabelDefs inside this DefDef
    val res = new collection.mutable.HashMap[Tree, List[LabelDef]]()

    val t = new TreeTraverser {
      var outerRhs: Tree = tree

      def traverse(tree: tpd.Tree)(implicit ctx: Context): Unit = tree match {
        case t: DefDef =>
          if (t.symbol is Flags.Label)
            res.put(outerRhs, t :: res.getOrElse(outerRhs, Nil))
          else outerRhs = t
          traverseChildren(t)
        case _ => traverseChildren(tree)
      }
    }

    t.traverse(tree)
    res.toMap
  }

  // todo: remove
  def isMaybeBoxed(sym: Symbol) = {
    (sym == ObjectClass) ||
      (sym == JavaSerializableClass) ||
      (sym == defn.ComparableClass) ||
      (sym derivesFrom BoxedNumberClass) ||
      (sym derivesFrom BoxedCharacterClass) ||
      (sym derivesFrom BoxedBooleanClass)
  }

  def getSingleOutput: Option[AbstractFile] = None // todo: implement


  def getGenericSignature(sym: Symbol, owner: Symbol): String = null // todo: implement

  def getStaticForwarderGenericSignature(sym: Symbol, moduleClass: Symbol): String = null // todo: implement


  def sourceFileFor(cu: CompilationUnit): String = cu.source.file.name



  implicit def positionHelper(a: Position): PositionHelper = new PositionHelper {
    def isDefined: Boolean = a.exists
    def line: Int = sourcePos(a).line + 1
    def finalPosition: Position = a
  }

  implicit def constantHelper(a: Constant): ConstantHelper = new ConstantHelper {
    def booleanValue: Boolean = a.booleanValue
    def longValue: Long = a.longValue
    def byteValue: Byte = a.byteValue
    def stringValue: String = a.stringValue
    def symbolValue: Symbol = a.symbolValue
    def floatValue: Float = a.floatValue
    def value: Any = a.value
    def tag: ConstantTag = a.tag
    def typeValue: Type = a.typeValue
    def shortValue: Short = a.shortValue
    def intValue: Int = a.intValue
    def doubleValue: Double = a.doubleValue
    def charValue: Char = a.charValue
  }


  implicit def treeHelper(a: Tree): TreeHelper = new TreeHelper {
    def symbol: Symbol = a.symbol

    def pos: Position = a.pos

    def isEmpty: Boolean = a.isEmpty

    def tpe: Type = a.tpe

    def exists(pred: (Tree) => Boolean): Boolean = a.find(pred).isDefined
  }


  implicit def annotHelper(a: Annotation): AnnotationHelper = new AnnotationHelper {
    def atp: Type = a.tree.tpe

    def assocs: List[(Name, Tree)] = assocsFromApply(a.tree)

    def symbol: Symbol = a.tree.symbol

    def args: List[Tree] = List.empty // those arguments to scala-defined annotations. they are never emmited
  }

  def assocsFromApply(tree: Tree) = {
    tree match {
      case Apply(fun, args) =>
        fun.tpe.widen match {
          case MethodType(names, _) =>
            names zip args
        }
    }
  }


  implicit def nameHelper(n: Name): NameHelper = new NameHelper {
    def toTypeName: Name = n.toTypeName
    def isTypeName: Boolean = n.isTypeName
    def toTermName: Name = n.toTermName
    def dropModule: Name = n.stripModuleClassSuffix

    def len: Int = n.length
    def offset: Int = n.start
    def isTermName: Boolean = n.isTermName
    def startsWith(s: String): Boolean = n.startsWith(s)
  }


  implicit def symHelper(sym: Symbol): SymbolHelper = new SymbolHelper {
    // names
    def fullName(sep: Char): String = sym.showFullName
    def fullName: String = sym.showFullName
    def simpleName: Name = sym.name
    def javaSimpleName: Name = toDenot(sym).name // addModuleSuffix(simpleName.dropLocal)
    def javaBinaryName: Name = toDenot(sym).fullNameSeparated("/") // addModuleSuffix(fullNameInternal('/'))
    def javaClassName: String = toDenot(sym).fullName.toString// addModuleSuffix(fullNameInternal('.')).toString
    def name: Name = sym.name
    def rawname: Name = sym.name // todo ????

    // types
    def info: Type = toDenot(sym).info
    def tpe: Type = toDenot(sym).info // todo whats the differentce between tpe and info?
    def thisType: Type = toDenot(sym).thisType

    // tests
    def isClass: Boolean = {
      sym.isPackageObject || (sym.isClass)
    }
    def isType: Boolean = sym.isType
    def isAnonymousClass: Boolean = toDenot(sym).isAnonymousClass
    def isConstructor: Boolean = toDenot(sym).isConstructor
    def isAnonymousFunction: Boolean = toDenot(sym).isAnonymousFunction
    def isMethod: Boolean = sym is Flags.Method
    def isPublic: Boolean =  sym.flags.is(Flags.EmptyFlags, Flags.Private | Flags.Protected)
    def isSynthetic: Boolean = sym is Flags.Synthetic
    def isPackageClass: Boolean = sym is Flags.PackageClass
    def isModuleClass: Boolean = sym is Flags.ModuleClass
    def isModule: Boolean = sym is Flags.Module
    def isStrictFP: Boolean = false // todo: implement
    def isLabel: Boolean = sym is Flags.Label
    def hasPackageFlag: Boolean = sym is Flags.Package
    def isImplClass: Boolean = sym is Flags.ImplClass
    def isInterface: Boolean = (sym is Flags.PureInterface) || (sym is Flags.Trait)
    def hasGetter: Boolean = false // used only for generaration of beaninfo todo: implement
    def isGetter: Boolean = toDenot(sym).isGetter
    def isSetter: Boolean = toDenot(sym).isSetter
    def isGetClass: Boolean = sym eq defn.Any_getClass
    def isJavaDefined: Boolean = sym is Flags.JavaDefined
    def isJavaDefaultMethod: Boolean = !((sym is Flags.Deferred)  || toDenot(sym).isClassConstructor)
    def isDeferred: Boolean = sym is Flags.Deferred
    def isPrivate: Boolean = sym is Flags.Private
    def getsJavaFinalFlag: Boolean =
      isFinal &&  !toDenot(sym).isClassConstructor && !(sym is Flags.Mutable) &&  !(sym.enclosingClass is Flags.Trait)

    def getsJavaPrivateFlag: Boolean =
      isPrivate //|| (sym.isPrimaryConstructor && sym.owner.isTopLevelModuleClass)

    def isFinal: Boolean = sym is Flags.Final
    def isStaticMember: Boolean = (sym ne NoSymbol) &&
      ((sym is Flags.JavaStatic) || (owner is Flags.ImplClass) || toDenot(sym).hasAnnotation(ctx.definitions.ScalaStaticAnnot))
      // guard against no sumbol cause this code is executed to select which call type(static\dynamic) to use to call array.clone

    def isBottomClass: Boolean = (sym ne defn.NullClass) && (sym ne defn.NothingClass)
    def isBridge: Boolean = sym is Flags.Bridge
    def isArtifact: Boolean = sym is Flags.Artifact
    def hasEnumFlag: Boolean = sym is Flags.Enum
    def hasAccessBoundary: Boolean = sym.accessBoundary(defn.RootClass) ne defn.RootClass
    def isVarargsMethod: Boolean = sym is Flags.JavaVarargs
    def isDeprecated: Boolean = false
    def isMutable: Boolean = sym is Flags.Mutable
    def hasAbstractFlag: Boolean =
      (sym is Flags.Abstract) || (sym is Flags.JavaInterface) || (sym is Flags.Trait)
    def hasModuleFlag: Boolean = sym is Flags.Module
    def isSynchronized: Boolean = sym is Flags.Synchronized
    def isNonBottomSubClass(other: Symbol): Boolean = sym.derivesFrom(other)
    def hasAnnotation(ann: Symbol): Boolean = toDenot(sym).hasAnnotation(ann)
    def shouldEmitForwarders: Boolean =
      (sym is Flags.Module) && !(sym is Flags.ImplClass) && sym.isStatic
    def isJavaEntryPoint: Boolean = CollectEntryPoints.isJavaEntryPoint(sym)

    def isClassConstructor: Boolean = toDenot(sym).isClassConstructor

    /**
     * True for module classes of modules that are top-level or owned only by objects. Module classes
     * for such objects will get a MODULE$ flag and a corresponding static initializer.
     */
    def isStaticModuleClass: Boolean =
      (sym is Flags.Module) && {
        // scalac uses atPickling here
        // this would not work if modules are created after pickling
        // for example by specialization
        val original = toDenot(sym).initial
        val validity = original.validFor
        val shiftedContext = ctx.withPhase(validity.phaseId)
        toDenot(sym)(shiftedContext).isStatic(shiftedContext)
      }

    def isStaticConstructor: Boolean = (isStaticMember && isClassConstructor) || (sym.name eq core.Names.STATIC_CONSTRUCTOR)


    // navigation
    def owner: Symbol = toDenot(sym).owner
    def rawowner: Symbol = {
      originalOwner
    }
    def originalOwner: Symbol = {
      try {
        if (sym.exists) {
          val original = toDenot(sym).initial
          val validity = original.validFor
          val shiftedContext = ctx.withPhase(validity.phaseId)
          val r = toDenot(sym)(shiftedContext).maybeOwner.enclosingClass(shiftedContext)
          r
        } else NoSymbol
      } catch {
        case e: NotDefinedHere => NoSymbol // todo: do we have a method to tests this?
      }
    }
    def parentSymbols: List[Symbol] = toDenot(sym).info.parents.map(_.typeSymbol)
    def superClass: Symbol =  {
      val t = toDenot(sym).asClass.superClass
      if (t.exists) t
      else if (sym is Flags.ModuleClass) {
        // workaround #371

        println(s"Warning: mocking up superclass for $sym")
        ObjectClass
      }
      else t
    }
    def enclClass: Symbol = toDenot(sym).enclosingClass
    def linkedClassOfClass: Symbol = linkedClass
    def linkedClass: Symbol = {
      toDenot(sym)(ctx).linkedClass(ctx)
    } //exitingPickler(sym.linkedClassOfClass)
    def companionClass: Symbol = toDenot(sym).companionClass
    def companionModule: Symbol = toDenot(sym).companionModule
    def companionSymbol: Symbol = if (sym is Flags.Module) companionClass else companionModule
    def moduleClass: Symbol = toDenot(sym).moduleClass
    def enclosingClassSym: Symbol = {
      if (this.isClass) {
        val ct = ctx.withPhase(ctx.flattenPhase.prev)
        toDenot(sym)(ct).owner.enclosingClass(ct)
      }
      else sym.enclosingClass(ctx.withPhase(ctx.flattenPhase.prev))
    } //todo is handled specially for JavaDefined symbols in scalac



    // members
    def primaryConstructor: Symbol = toDenot(sym).primaryConstructor

    /** For currently compiled classes: All locally defined classes including local classes.
     *  The empty list for classes that are not currently compiled.
     */
    def nestedClasses: List[Symbol] = definedClasses(ctx.flattenPhase)

    /** For currently compiled classes: All classes that are declared as members of this class
     *  (but not inherited ones). The empty list for classes that are not currently compiled.
     */
    def memberClasses: List[Symbol] = definedClasses(ctx.lambdaLiftPhase)

    private def definedClasses(phase: Phase) =
      if (sym.isDefinedInCurrentRun)
        ctx.atPhase(phase) { implicit ctx =>
          toDenot(sym).info.decls.filter(_.isClass).toList
        }
      else Nil

    def annotations: List[Annotation] = Nil
    def companionModuleMembers: List[Symbol] =  {
      // phase travel to exitingPickler: this makes sure that memberClassesOf only sees member classes,
      // not local classes of the companion module (E in the exmaple) that were lifted by lambdalift.
      if (linkedClass.isTopLevelModuleClass) /*exitingPickler*/ linkedClass.memberClasses
      else Nil
    }
    def fieldSymbols: List[Symbol] = {
      toDenot(sym).info.decls.filter(p => p.isTerm && !p.is(Flags.Method)).toList
    }
    def methodSymbols: List[Symbol] =
      for (f <- toDenot(sym).info.decls.toList if f.isMethod && f.isTerm && !f.isModule) yield f
    def serialVUID: Option[Long] = None


    def freshLocal(cunit: CompilationUnit, name: String, tpe: Type, pos: Position, flags: Flags): Symbol = {
      ctx.newSymbol(sym, name.toTermName, FlagSet(flags), tpe, NoSymbol, pos)
    }

    def getter(clz: Symbol): Symbol = decorateSymbol(sym).getter
    def setter(clz: Symbol): Symbol = decorateSymbol(sym).setter

    def moduleSuffix: String = "" // todo: validate that names already have $ suffix
    def outputDirectory: AbstractFile = DottyBackendInterface.this.outputDirectory
    def pos: Position = sym.pos

    def throwsAnnotations: List[Symbol] = Nil

    /**
     * All interfaces implemented by a class, except for those inherited through the superclass.
     *
     */
    def superInterfaces: List[Symbol] = decorateSymbol(sym).directlyInheritedTraits

    /**
     * True for module classes of package level objects. The backend will generate a mirror class for
     * such objects.
     */
    def isTopLevelModuleClass: Boolean = sym.isModuleClass && sym.isStatic

    /**
     * This is basically a re-implementation of sym.isStaticOwner, but using the originalOwner chain.
     *
     * The problem is that we are interested in a source-level property. Various phases changed the
     * symbol's properties in the meantime, mostly lambdalift modified (destructively) the owner.
     * Therefore, `sym.isStatic` is not what we want. For example, in
     *   object T { def f { object U } }
     * the owner of U is T, so UModuleClass.isStatic is true. Phase travel does not help here.
     */
    def isOriginallyStaticOwner: Boolean = sym.isStatic


    def addRemoteRemoteExceptionAnnotation: Unit = ()

    def samMethod(): Symbol =
      toDenot(sym).info.abstractTermMembers.headOption.getOrElse(toDenot(sym).info.member(nme.apply)).symbol
  }


  implicit def typeHelper(tp: Type): TypeHelper = new TypeHelper {
    def member(string: Name): Symbol = tp.member(string.toTermName).symbol

    def isFinalType: Boolean = tp.typeSymbol is Flags.Final //in scalac checks for type parameters. Why? Aren't they gone by backend?

    def underlying: Type = tp match {
      case t: TypeProxy => t.underlying
      case _ => tp
    }

    def paramTypes: List[Type] = tp.firstParamTypes

    def <:<(other: Type): Boolean = tp <:< other

    def memberInfo(s: Symbol): Type = tp.memberInfo(s)

    def decls: List[Symbol] = tp.decls.map(_.symbol).toList

    def members: List[Symbol] =
      tp.memberDenots(takeAllFilter, (name, buf) => buf ++= tp.member(name).alternatives).map(_.symbol).toList

    def typeSymbol: Symbol = tp.widenDealias.typeSymbol

    def =:=(other: Type): Boolean = tp =:= other

    def membersBasedOnFlags(excludedFlags: Flags, requiredFlags: Flags): List[Symbol] =
      tp.membersBasedOnFlags(FlagSet(requiredFlags), FlagSet(excludedFlags)).map(_.symbol).toList

    def resultType: Type = tp.resultType

    def toTypeKind(ct: BCodeHelpers)(storage: ct.BCInnerClassGen): ct.bTypes.BType = {
      import ct.bTypes._
      val defn = ctx.definitions
      import coreBTypes._
      import Types._
      /**
       * Primitive types are represented as TypeRefs to the class symbol of, for example, scala.Int.
       * The `primitiveTypeMap` maps those class symbols to the corresponding PrimitiveBType.
       */
      def primitiveOrClassToBType(sym: Symbol): BType = {
        assert(sym.isClass, sym)
        assert(sym != ArrayClass || isCompilingArray, sym)
        primitiveTypeMap.getOrElse(sym.asInstanceOf[ct.bTypes.coreBTypes.bTypes.int.Symbol],
          storage.getClassBTypeAndRegisterInnerClass(sym.asInstanceOf[ct.int.Symbol])).asInstanceOf[BType]
      }

      /**
       * When compiling Array.scala, the type parameter T is not erased and shows up in method
       * signatures, e.g. `def apply(i: Int): T`. A TyperRef to T is replaced by ObjectReference.
       */
      def nonClassTypeRefToBType(sym: Symbol): ClassBType = {
        assert(sym.isType && isCompilingArray, sym)
        ObjectReference.asInstanceOf[ct.bTypes.ClassBType]
      }

      tp.widenDealias match {
        case JavaArrayType(el) =>ArrayBType(el.toTypeKind(ct)(storage)) // Array type such as Array[Int] (kept by erasure)
        case t: TypeRef =>
          t.info match {

            case _ =>
              if (!t.symbol.isClass) nonClassTypeRefToBType(t.symbol)  // See comment on nonClassTypeRefToBType
              else primitiveOrClassToBType(t.symbol) // Common reference to a type such as scala.Int or java.lang.String
          }
        case Types.ClassInfo(_, sym, _, _, _)           => primitiveOrClassToBType(sym) // We get here, for example, for genLoadModule, which invokes toTypeKind(moduleClassSymbol.info)

        case t: MethodType => // triggers for LabelDefs
          t.resultType.toTypeKind(ct)(storage)

        /* AnnotatedType should (probably) be eliminated by erasure. However we know it happens for
         * meta-annotated annotations (@(ann @getter) val x = 0), so we don't emit a warning.
         * The type in the AnnotationInfo is an AnnotatedTpe. Tested in jvm/annotations.scala.
         */
        case a @ AnnotatedType(t, _) =>
          debuglog(s"typeKind of annotated type $a")
          t.toTypeKind(ct)(storage)

        /* ExistentialType should (probably) be eliminated by erasure. We know they get here for
         * classOf constants:
         *   class C[T]
         *   class T { final val k = classOf[C[_]] }
         */
       /* case e @ ExistentialType(_, t) =>
          debuglog(s"typeKind of existential type $e")
          t.toTypeKind(ctx)(storage)*/

        /* The cases below should probably never occur. They are kept for now to avoid introducing
         * new compiler crashes, but we added a warning. The compiler / library bootstrap and the
         * test suite don't produce any warning.
         */

        case tp =>
          ctx.warning(
            s"an unexpected type representation reached the compiler backend while compiling $currentUnit: $tp. " +
              "If possible, please file a bug on issues.scala-lang.org.")

          tp match {
            case tp: ThisType if tp.cls == ArrayClass => ObjectReference.asInstanceOf[ct.bTypes.ClassBType] // was introduced in 9b17332f11 to fix SI-999, but this code is not reached in its test, or any other test
            case tp: ThisType                         => storage.getClassBTypeAndRegisterInnerClass(tp.cls.asInstanceOf[ct.int.Symbol])
           // case t: SingletonType                   => primitiveOrClassToBType(t.classSymbol)
            case t: SingletonType                     => t.underlying.toTypeKind(ct)(storage)
            case t: RefinedType                       =>  t.parent.toTypeKind(ct)(storage) //parents.map(_.toTypeKind(ct)(storage).asClassBType).reduceLeft((a, b) => a.jvmWiseLUB(b))
          }
      }
    }

    def summaryString: String = tp.showSummary

    def params: List[Symbol] =
      Nil // backend uses this to emmit annotations on parameter lists of forwarders
          // to static methods of companion class
          // in Dotty this link does not exists: there is no way to get from method type
          // to inner symbols of DefDef
          // todo: somehow handle.

    def parents: List[Type] = tp.parents
  }



  object Assign extends AssignDeconstructor {
    def _1: Tree = field.lhs
    def _2: Tree = field.rhs
  }

  object Select extends SelectDeconstructor {

    var desugared: tpd.Select = null

    override def isEmpty: Boolean =
      desugared eq null

    def _1: Tree =  desugared.qualifier

    def _2: Name = desugared.name

    override def unapply(s: Select): this.type = {
      s match {
        case t: tpd.Select => desugared = t
        case t: Ident  =>
          desugarIdent(t) match {
            case Some(t) => desugared = t
            case None => desugared = null
          }
        case _ => desugared = null
      }

      this
    }
  }

  object Apply extends ApplyDeconstructor {
    def _1: Tree = field.fun
    def _2: List[Tree] = field.args
  }

  object If extends IfDeconstructor {
    def _1: Tree = field.cond
    def _2: Tree = field.thenp
    def _3: Tree = field.elsep
  }

  object ValDef extends ValDefDeconstructor {
    def _1: Modifiers = field.mods
    def _2: Name = field.name
    def _3: Tree = field.tpt
    def _4: Tree = field.rhs
  }

  object ApplyDynamic extends ApplyDynamicDeconstructor {
    def _1: Tree = ???
    def _2: List[Tree] = ???
  }

  // todo: this product1s should also eventually become name-based pattn matching
  object Literal extends LiteralDeconstructor {
    def get = field.const
  }

  object Throw extends ThrowDeconstructor {
    def get = field.args.head

    override def unapply(s: Throw): DottyBackendInterface.this.Throw.type = {
      if (s.fun.symbol eq defn.throwMethod) {
        field = s
      } else {
        field = null
      }
      this
    }
  }

  object New extends NewDeconstructor {
    def get = field.tpt.tpe
  }

  object This extends ThisDeconstructor {
    def get = field.qual
    def apply(s: Symbol): This = tpd.This(s.asClass)
  }

  object Return extends ReturnDeconstructor {
    def get = field.expr
  }

  object Ident extends IdentDeconstructor {
    def get = field.name
  }

  object Alternative extends AlternativeDeconstructor {
    def get = field.trees
  }

  object Constant extends ConstantDeconstructor {
    def get = field.value
  }
  object ThrownException extends ThrownException {
    def unapply(a: Annotation): Option[Symbol] = None // todo
  }

  object Try extends TryDeconstructor {
    def _1: Tree = field.expr
    def _2: List[Tree] = field.cases
    def _3: Tree = field.finalizer
  }

  object LabelDef extends LabelDeconstructor {
    def _1: Name = field.name
    def _2: List[Symbol] = field.vparamss.flatMap(_.map(_.symbol))
    def _3: Tree = field.rhs

    override def unapply(s: LabelDef): DottyBackendInterface.this.LabelDef.type = {
      if (s.symbol is Flags.Label) this.field = s
      else this.field = null
      this
    }
  }

  object Typed extends TypedDeconstrutor {
    def _1: Tree = field.expr
    def _2: Tree = field.tpt
  }
  object Super extends SuperDeconstructor {
    def _1: Tree = field.qual
    def _2: Name = field.mix
  }
  object ArrayValue extends ArrayValueDeconstructor {
    def _1: Type = field.tpe match {
      case JavaArrayType(elem) => elem
      case _ =>
        ctx.error(s"JavaSeqArray with type ${field.tpe} reached backend: $field", field.pos)
        ErrorType
    }
    def _2: List[Tree] = field.elems
  }
  object Match extends MatchDeconstructor {
    def _1: Tree = field.selector
    def _2: List[Tree] = field.cases
  }
  object Block extends BlockDeconstructor {
    def _1: List[Tree] = field.stats
    def _2: Tree = field.expr
  }
  object TypeApply extends TypeApplyDeconstructor {
    def _1: Tree = field.fun
    def _2: List[Tree] = field.args
  }
  object CaseDef extends CaseDeconstructor {
    def _1: Tree = field.pat
    def _2: Tree = field.guard
    def _3: Tree = field.body
  }

  object DefDef extends DefDefDeconstructor {
    def _1: Modifiers = field.mods
    def _2: Name = field.name
    def _3: List[TypeDef] = field.tparams
    def _4: List[List[ValDef]] = field.vparamss
    def _5: Tree = field.tpt
    def _6: Tree = field.rhs
  }

  object ModuleDef extends ModuleDefDeconstructor {
    def _1: Modifiers = ???
    def _2: Name = ???
    def _3: Tree = ???
  }

  object Template extends TemplateDeconstructor {
    def _1: List[Tree] = field.parents
    def _2: ValDef = field.self
    def _3: List[Tree] = field.constr :: field.body
  }

  object Bind extends BindDeconstructor {
    def _1: Name = field.name
    def _2: Tree = field.body
  }

  object ClassDef extends ClassDefDeconstructor {
    def _1: Modifiers = field.mods
    def _2: Name = field.name
    def _4: Template = field.rhs.asInstanceOf[Template]
    def _3: List[TypeDef] = Nil
  }

  object Closure extends ClosureDeconstructor {
    def _1 = field.env
    def _2 = field.meth
    def _3 = {
      val t = field.tpt.tpe.typeSymbol
      if (t.exists) t
      else {
        val arity = field.meth.tpe.widenDealias.paramTypes.size - _1.size
        val returnsUnit = field.meth.tpe.widenDealias.resultType.classSymbol == UnitClass
        if (returnsUnit)
          ctx.requiredClass(("scala.compat.java8.JProcedure" + arity).toTermName)
        else ctx.requiredClass(("scala.compat.java8.JFunction" + arity).toTermName)
      }
    }
  }

  def currentUnit = ctx.compilationUnit
}
