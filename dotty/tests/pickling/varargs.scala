object varargs {
  List(1, 2, 3)
  def g(x: Int*) = x.length
  g(1, 2, 3, 4)
  val x = if (true) 1 else 2
  def foo[T](x: T, y: T): T = x
  foo(1, 2)
  val xs = 1 :: 2 :: Nil
  g(xs: _*)
  g(Nil: _*)
  g(1)
  g()
}
