import scala.tools.partest.JavapTest
import scala.tools.nsc.Settings

// see repl-javap-memfun.java for the complementary version
object Test extends JavapTest {
  override def transformSettings(s: Settings) = { s.Ydelambdafy.value = "method" ; s }
  def code = """
    |object Betty {
    | List(1,2,3) count (_ % 2 != 0)
    | def f = List(1,2,3) filter (_ % 2 != 0) map (_ * 2)
    | def g = List(1,2,3) filter (_ % 2 == 0) map (_ * 3) map (_ + 1)
    |}
    |:javap -fun Betty#g
  """.stripMargin

  // three anonfuns of Betty#g
  override def yah(res: Seq[String]) = {
    import PartialFunction.{ cond => when }
    val r = """\s*private static final .* \$anonfun\$\d+\(.*""".r
    def filtered = res filter (when(_) { case r(_*) => true })
    3 == filtered.size
  }
}
