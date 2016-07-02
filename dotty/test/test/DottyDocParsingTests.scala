package test

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.ast.Trees._

import org.junit.Assert._
import org.junit.Test

class DottyDocParsingTests extends DottyDocTest {

  @Test def noComment = {
    import dotty.tools.dotc.ast.untpd._
    val source = "class Class"

    checkFrontend(source) {
      case PackageDef(_, Seq(c: TypeDef)) =>
        assert(c.rawComment == None, "Should not have a comment, mainly used for exhaustive tests")
    }
  }

  @Test def singleClassInPackage = {
    val source =
      """
      |package a
      |
      |/** Hello world! */
      |class Class(val x: String)
      """.stripMargin

    checkFrontend(source) {
      case PackageDef(_, Seq(t @ TypeDef(name, _))) if name.toString == "Class" =>
        checkDocString(t.rawComment, "/** Hello world! */")
    }
  }

  @Test def multipleOpenedOnSingleClassInPackage = {
    val source =
      """
      |package a
      |
      |/** Hello /* multiple open */ world! */
      |class Class(val x: String)
      """.stripMargin

    checkFrontend(source) {
      case PackageDef(_, Seq(t @ TypeDef(name, _))) if name.toString == "Class" =>
        checkDocString(t.rawComment, "/** Hello /* multiple open */ world! */")
    }
  }
  @Test def multipleClassesInPackage = {
    val source =
      """
      |package a
      |
      |/** Class1 docstring */
      |class Class1(val x: String)
      |
      |/** Class2 docstring */
      |class Class2(val x: String)
      """.stripMargin

    checkCompile("frontend", source) { (_, ctx) =>
      ctx.compilationUnit.untpdTree match {
        case PackageDef(_, Seq(c1 @ TypeDef(_,_), c2 @ TypeDef(_,_))) => {
          checkDocString(c1.rawComment, "/** Class1 docstring */")
          checkDocString(c2.rawComment, "/** Class2 docstring */")
        }
      }
    }
  }

  @Test def singleCaseClassWithoutPackage = {
    val source =
      """
      |/** Class without package */
      |case class Class(val x: Int)
      """.stripMargin

    checkFrontend(source) {
      case PackageDef(_, Seq(t @ TypeDef(_,_))) => checkDocString(t.rawComment, "/** Class without package */")
    }
  }

  @Test def SingleTraitWihoutPackage = {
    val source = "/** Trait docstring */\ntrait Trait"

    checkFrontend(source) {
      case PackageDef(_, Seq(t @ TypeDef(_,_))) => checkDocString(t.rawComment, "/** Trait docstring */")
    }
  }

  @Test def multipleTraitsWithoutPackage = {
    val source =
      """
      |/** Trait1 docstring */
      |trait Trait1
      |
      |/** Trait2 docstring */
      |trait Trait2
      """.stripMargin

    checkFrontend(source) {
      case PackageDef(_, Seq(t1 @ TypeDef(_,_), t2 @ TypeDef(_,_))) => {
        checkDocString(t1.rawComment, "/** Trait1 docstring */")
        checkDocString(t2.rawComment, "/** Trait2 docstring */")
      }
    }
  }

  @Test def multipleMixedEntitiesWithPackage = {
    val source =
      """
      |/** Trait1 docstring */
      |trait Trait1
      |
      |/** Class2 docstring */
      |class Class2(val x: Int)
      |
      |/** CaseClass3 docstring */
      |case class CaseClass3()
      |
      |case class NoComment()
      |
      |/** AbstractClass4 docstring */
      |abstract class AbstractClass4(val x: Int)
      """.stripMargin

    checkFrontend(source) {
      case PackageDef(_, Seq(t1 @ TypeDef(_,_), c2 @ TypeDef(_,_), cc3 @ TypeDef(_,_), _, ac4 @ TypeDef(_,_))) => {
        checkDocString(t1.rawComment, "/** Trait1 docstring */")
        checkDocString(c2.rawComment, "/** Class2 docstring */")
        checkDocString(cc3.rawComment, "/** CaseClass3 docstring */")
        checkDocString(ac4.rawComment, "/** AbstractClass4 docstring */")
      }
    }
  }

  @Test def nestedClass = {
    val source =
      """
      |/** Outer docstring */
      |class Outer {
      |  /** Inner docstring */
      |  class Inner(val x: Int)
      |}
      """.stripMargin

    checkFrontend(source) {
      case PackageDef(_, Seq(outer @ TypeDef(_, tpl @ Template(_,_,_,_)))) => {
        checkDocString(outer.rawComment, "/** Outer docstring */")
        tpl.body match {
          case (inner @ TypeDef(_,_)) :: _ => checkDocString(inner.rawComment, "/** Inner docstring */")
          case _ => assert(false, "Couldn't find inner class")
        }
      }
    }
  }

  @Test def nestedClassThenOuter = {
    val source =
      """
      |/** Outer1 docstring */
      |class Outer1 {
      |  /** Inner docstring */
      |  class Inner(val x: Int)
      |}
      |
      |/** Outer2 docstring */
      |class Outer2
      """.stripMargin

    checkFrontend(source) {
      case PackageDef(_, Seq(o1 @ TypeDef(_, tpl @ Template(_,_,_,_)), o2 @ TypeDef(_,_))) => {
        checkDocString(o1.rawComment, "/** Outer1 docstring */")
        checkDocString(o2.rawComment, "/** Outer2 docstring */")
        tpl.body match {
          case (inner @ TypeDef(_,_)) :: _ => checkDocString(inner.rawComment, "/** Inner docstring */")
          case _ => assert(false, "Couldn't find inner class")
        }
      }
    }
  }

  @Test def objects = {
    val source =
      """
      |package p
      |
      |/** Object1 docstring */
      |object Object1
      |
      |/** Object2 docstring */
      |object Object2
      """.stripMargin

    checkFrontend(source) {
      case p @ PackageDef(_, Seq(o1: MemberDef[Untyped], o2: MemberDef[Untyped])) => {
        assertEquals(o1.name.toString, "Object1")
        checkDocString(o1.rawComment, "/** Object1 docstring */")
        assertEquals(o2.name.toString, "Object2")
        checkDocString(o2.rawComment, "/** Object2 docstring */")
      }
    }
  }

  @Test def objectsNestedClass = {
    val source =
      """
      |package p
      |
      |/** Object1 docstring */
      |object Object1
      |
      |/** Object2 docstring */
      |object Object2 {
      |  class A1
      |  /** Inner docstring */
      |  class Inner
      |}
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case p @ PackageDef(_, Seq(o1: ModuleDef, o2: ModuleDef)) => {
        assert(o1.name.toString == "Object1")
        checkDocString(o1.rawComment, "/** Object1 docstring */")
        assert(o2.name.toString == "Object2")
        checkDocString(o2.rawComment, "/** Object2 docstring */")

        o2.impl.body match {
          case _ :: (inner @ TypeDef(_,_)) :: _ => checkDocString(inner.rawComment, "/** Inner docstring */")
          case _ => assert(false, "Couldn't find inner class")
        }
      }
    }
  }

  @Test def packageObject = {
    val source =
      """
      |/** Package object docstring */
      |package object foo {
      |  /** Boo docstring */
      |  case class Boo()
      |
      |  /** Trait docstring */
      |  trait Trait
      |
      |  /** InnerObject docstring */
      |  object InnerObject {
      |    /** InnerClass docstring */
      |    class InnerClass
      |  }
      |}
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case PackageDef(_, Seq(p: ModuleDef)) => {
        checkDocString(p.rawComment, "/** Package object docstring */")

        p.impl.body match {
          case (b: TypeDef) :: (t: TypeDef) :: (o: ModuleDef) :: Nil => {
            checkDocString(b.rawComment, "/** Boo docstring */")
            checkDocString(t.rawComment, "/** Trait docstring */")
            checkDocString(o.rawComment, "/** InnerObject docstring */")
            checkDocString(o.impl.body.head.asInstanceOf[TypeDef].rawComment, "/** InnerClass docstring */")
          }
          case _ => assert(false, "Incorrect structure inside package object")
        }
      }
    }
  }

  @Test def multipleDocStringsBeforeEntity = {
    val source =
      """
      |/** First comment */
      |/** Second comment */
      |/** Real comment */
      |class Class
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case PackageDef(_, Seq(c: TypeDef)) =>
        checkDocString(c.rawComment, "/** Real comment */")
    }
  }

  @Test def multipleDocStringsBeforeAndAfter = {
    val source =
      """
      |/** First comment */
      |/** Second comment */
      |/** Real comment */
      |class Class
      |/** Following comment 1 */
      |/** Following comment 2 */
      |/** Following comment 3 */
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case PackageDef(_, Seq(c: TypeDef)) =>
        checkDocString(c.rawComment, "/** Real comment */")
    }
  }

  @Test def valuesWithDocString = {
    val source =
      """
      |object Object {
      |  /** val1 */
      |  val val1 = 1
      |
      |  /** val2 */
      |  val val2: Int = 2
      |  /** bogus docstring */
      |
      |  /** bogus docstring */
      |  /** val3 */
      |  val val3: List[Int] = 1 :: 2 :: 3 :: Nil
      |}
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case PackageDef(_, Seq(o: ModuleDef)) => {
        o.impl.body match {
          case (v1: MemberDef) :: (v2: MemberDef) :: (v3: MemberDef) :: Nil => {
            checkDocString(v1.rawComment, "/** val1 */")
            checkDocString(v2.rawComment, "/** val2 */")
            checkDocString(v3.rawComment, "/** val3 */")
          }
          case _ => assert(false, "Incorrect structure inside object")
        }
      }
    }
  }

  @Test def varsWithDocString = {
    val source =
      """
      |object Object {
      |  /** var1 */
      |  var var1 = 1
      |
      |  /** var2 */
      |  var var2: Int = 2
      |  /** bogus docstring */
      |
      |  /** bogus docstring */
      |  /** var3 */
      |  var var3: List[Int] = 1 :: 2 :: 3 :: Nil
      |}
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case PackageDef(_, Seq(o: ModuleDef)) => {
        o.impl.body match {
          case (v1: MemberDef) :: (v2: MemberDef) :: (v3: MemberDef) :: Nil => {
            checkDocString(v1.rawComment, "/** var1 */")
            checkDocString(v2.rawComment, "/** var2 */")
            checkDocString(v3.rawComment, "/** var3 */")
          }
          case _ => assert(false, "Incorrect structure inside object")
        }
      }
    }
  }

  @Test def defsWithDocString = {
    val source =
      """
      |object Object {
      |  /** def1 */
      |  def def1 = 1
      |
      |  /** def2 */
      |  def def2: Int = 2
      |  /** bogus docstring */
      |
      |  /** bogus docstring */
      |  /** def3 */
      |  def def3: List[Int] = 1 :: 2 :: 3 :: Nil
      |}
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case PackageDef(_, Seq(o: ModuleDef)) => {
        o.impl.body match {
          case (v1: MemberDef) :: (v2: MemberDef) :: (v3: MemberDef) :: Nil => {
            checkDocString(v1.rawComment, "/** def1 */")
            checkDocString(v2.rawComment, "/** def2 */")
            checkDocString(v3.rawComment, "/** def3 */")
          }
          case _ => assert(false, "Incorrect structure inside object")
        }
      }
    }
  }

  @Test def typesWithDocString = {
    val source =
      """
      |object Object {
      |  /** type1 */
      |  type T1 = Int
      |
      |  /** type2 */
      |  type T2 = String
      |  /** bogus docstring */
      |
      |  /** bogus docstring */
      |  /** type3 */
      |  type T3 = T2
      |}
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case PackageDef(_, Seq(o: ModuleDef)) => {
        o.impl.body match {
          case (v1: MemberDef) :: (v2: MemberDef) :: (v3: MemberDef) :: Nil => {
            checkDocString(v1.rawComment, "/** type1 */")
            checkDocString(v2.rawComment, "/** type2 */")
            checkDocString(v3.rawComment, "/** type3 */")
          }
          case _ => assert(false, "Incorrect structure inside object")
        }
      }
    }
  }

  @Test def defInnerClass = {
    val source =
      """
      |object Foo {
      |  def foo() = {
      |    /** Innermost */
      |    class Innermost
      |  }
      |}
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case PackageDef(_, Seq(o: ModuleDef)) =>
        o.impl.body match {
          case (foo: MemberDef) :: Nil =>
            expectNoDocString(foo.rawComment)
          case _ => assert(false, "Incorrect structure inside object")
        }
    }
  }

  @Test def withExtends = {
    val source =
      """
      |trait Trait1
      |/** Class1 */
      |class Class1 extends Trait1
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case p @ PackageDef(_, Seq(_, c: TypeDef)) =>
        checkDocString(c.rawComment, "/** Class1 */")
    }
  }

  @Test def withAnnotation = {
    val source =
      """
      |/** Class1 */
      |@SerialVersionUID(1)
      |class Class1
      """.stripMargin

    import dotty.tools.dotc.ast.untpd._
    checkFrontend(source) {
      case p @ PackageDef(_, Seq(c: TypeDef)) =>
        checkDocString(c.rawComment, "/** Class1 */")
    }
  }
} /* End class */
