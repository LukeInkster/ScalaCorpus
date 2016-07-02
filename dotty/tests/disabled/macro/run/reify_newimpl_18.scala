import scala.reflect.runtime.universe._
import scala.tools.reflect.ToolBox
import scala.tools.reflect.Eval

object Test extends dotty.runtime.LegacyApp {
  class C[U: TypeTag] {
    type T = U
    val code = reify {
      List[T](2.asInstanceOf[T])
    }
    println(code.eval)
  }

  new C[Int]
}
