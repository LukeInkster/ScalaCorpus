trait A { def foo(a: A) : Unit }

trait C extends A { override def foo(a: A with Any) : Unit  }
