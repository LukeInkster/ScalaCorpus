package test.transform


import org.junit.{Assert, Test}
import test.DottyTest
import dotty.tools.dotc.core._
import dotty.tools.dotc.ast.Trees
import Contexts._
import Flags._
import Denotations._
import NameOps._
import Symbols._
import Types._
import Decorators._
import Trees._
import dotty.tools.dotc.transform.TreeTransforms.{TreeTransform, TreeTransformer}

class PostTyperTransformerTest extends DottyTest {
  /* FIXME: re-enable after adapting to new scheme

  @Test
  def shouldStripImports = checkCompile("frontend", "class A{ import scala.collection.mutable._; val d = 1}") {
    (tree, context) =>
      implicit val ctx = context
      class EmptyTransform extends TreeTransform {
        override def name: String = "empty"
        init(ctx, ctx.period.firstPhaseId, ctx.period.lastPhaseId)
      }
      val transformer = new PostTyperTransformer {
        override def transformations = Array(new EmptyTransform)

        override def name: String = "test"
      }
      val transformed = transformer.transform(tree)

      Assert.assertTrue("should strip imports",
        !transformed.toString.toLowerCase.contains("import")
      )
  }

  @Test
  def shouldStripNamedArgs = checkCompile("frontend", "class A{ def p(x:Int, y:Int= 2) = 1; p(1, y = 2)}") {
    (tree, context) =>
      implicit val ctx = context
      class EmptyTransform extends TreeTransform {
        override def name: String = "empty"
        init(ctx, ctx.period.firstPhaseId, ctx.period.lastPhaseId)
      }
      val transformer = new PostTyperTransformer {
        override def transformations = Array(new EmptyTransform)

        override def name: String = "test"
      }
      val transformed = transformer.transform(tree)

      Assert.assertTrue("should string named arguments",
        !transformed.toString.contains("NamedArg")
      )
  }

  @Test
  def shouldReorderExistingObjectsInPackage = checkCompile("frontend", "object A{}; class A{} ") {
    (tree, context) =>
      implicit val ctx = context
      class EmptyTransform extends TreeTransform {
        override def name: String = "empty"
        init(ctx, ctx.period.firstPhaseId, ctx.period.lastPhaseId)
      }
      val transformer = new PostTyperTransformer {
        override def transformations = Array(new EmptyTransform)

        override def name: String = "test"
      }
      val transformed = transformer.transform(tree).toString
      val classPattern = "TypeDef(Modifiers(,,List()),A,"
      val classPos = transformed.indexOf(classPattern)
      val moduleClassPattern = "TypeDef(Modifiers(final module,,List()),A$,"
      val modulePos = transformed.indexOf(moduleClassPattern)

      Assert.assertTrue("should reorder existing objects in package",
        classPos < modulePos
      )
  }

  @Test
  def shouldReorderExistingObjectsInBlock = checkCompile("frontend", "class D {def p = {object A{}; class A{}; 1}} ") {
    (tree, context) =>
      implicit val ctx = context
      class EmptyTransform extends TreeTransform {
        override def name: String = "empty"
        init(ctx, ctx.period.firstPhaseId, ctx.period.lastPhaseId)
      }
      val transformer = new PostTyperTransformer {
        override def transformations = Array(new EmptyTransform)

        override def name: String = "test"
      }
      val transformed = transformer.transform(tree).toString
      val classPattern = "TypeDef(Modifiers(,,List()),A,"
      val classPos = transformed.indexOf(classPattern)
      val moduleClassPattern = "TypeDef(Modifiers(final module,,List()),A$,"
      val modulePos = transformed.indexOf(moduleClassPattern)

      Assert.assertTrue("should reorder existing objects in block",
        classPos < modulePos
      )
  }

  @Test
  def shouldReorderExistingObjectsInTemplate = checkCompile("frontend", "class D {object A{}; class A{}; } ") {
    (tree, context) =>
      implicit val ctx = context
      class EmptyTransform extends TreeTransform {
        override def name: String = "empty"
        init(ctx, ctx.period.firstPhaseId, ctx.period.lastPhaseId)
      }
      val transformer = new PostTyperTransformer {
        override def transformations = Array(new EmptyTransform)

        override def name: String = "test"
      }
      val transformed = transformer.transform(tree).toString
      val classPattern = "TypeDef(Modifiers(,,List()),A,"
      val classPos = transformed.indexOf(classPattern)
      val moduleClassPattern = "TypeDef(Modifiers(final module,,List()),A$,"
      val modulePos = transformed.indexOf(moduleClassPattern)

      Assert.assertTrue("should reorder existing objects in template",
        classPos < modulePos
      )
  }*/
}
