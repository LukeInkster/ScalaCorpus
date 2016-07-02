object Test extends dotty.runtime.LegacyApp {
  import scala.reflect.runtime.universe._
  import scala.reflect.runtime.{currentMirror => cm}
  import scala.tools.reflect.ToolBox
  val tree = Apply(Select(Ident(TermName("Macros")), TermName("foo")), List(Literal(Constant(42))))
  try println(cm.mkToolBox().eval(tree))
  catch { case ex: Throwable =>  println(ex.getMessage) }
}
