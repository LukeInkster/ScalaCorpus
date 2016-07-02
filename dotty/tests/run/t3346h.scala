import scala.language.implicitConversions

object Test extends dotty.runtime.LegacyApp {
  trait Fundep[T, U] { def u(t: T): U }
  class C { def y = "x" }
  implicit val FundepStringC: Test.Fundep[String,Test.C] = new Fundep[String, C]{ def u(t: String) = new C }
  implicit def foo[T, U](x: T)(implicit y: Fundep[T, U]): U = y.u(x)
  println("x".y)
}
