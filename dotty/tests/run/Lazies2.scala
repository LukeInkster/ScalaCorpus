class T{ @volatile lazy val s = null}
object T{ @volatile lazy val s = null}
object Test{
  def main(args: Array[String]): Unit = {
    T.s
    (new T).s
  }
}
