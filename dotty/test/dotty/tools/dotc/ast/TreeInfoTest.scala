package dotty.tools.dotc
package ast

import org.junit.Test
import test.DottyTest
import core.Names._
import core.Types._
import core.Symbols._
import org.junit.Assert._

class TreeInfoTest extends DottyTest {

  import tpd._

  @Test
  def testDefPath = checkCompile("frontend", "class A { def bar = { val x = { val z = 0; 0} }} ") {
    (tree, context) =>
      implicit val ctx = context
      val xTree = tree.find(tree => tree.symbol.name == termName("x")).get
      val path = defPath(xTree.symbol, tree)
      assertEquals(List(
        ("PackageDef", EMPTY_PACKAGE),
        ("TypeDef", typeName("A")),
        ("Template", termName("<local A>")),
        ("DefDef", termName("bar")),
        ("Block", NoSymbol.name),
        ("ValDef", termName("x"))
      ), path.map(x => (x.productPrefix, x.symbol.name)))
  }
}
