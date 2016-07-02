object Test {

  trait Number
  trait Zero extends Number
  trait Succ[N <: Number](n: N) extends Number

  implicit def succ[N <: Number](implicit n: N): Succ[N] = new Succ[N](n) {}
  implicit def zero: Zero = new Zero{}

  implicitly[Zero]
  implicitly[Succ[Zero]]
  implicitly[Succ[Succ[Zero]]]
  implicitly[Succ[Succ[Succ[Zero]]]]

}
