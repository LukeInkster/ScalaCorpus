package test

import dotty._
import Predef.{any2stringadd => _, StringAdd => _, _}

object implicitDefs {

  implicit val x = 2 // error: type of implicit definition needs to be given explicitly
  implicit def y(x: Int) = 3 // error: result type of implicit definition needs to be given explicitly
  implicit def z(a: x.type): String = "" // error: implicit conversion may not have a parameter of singleton type
}
