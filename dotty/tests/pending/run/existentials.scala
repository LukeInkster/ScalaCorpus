import scala.language.existentials
import scala.language.reflectiveCalls

class Foo {
  class Line {
    case class Cell[T](var x: T)
    def f[T](x: Any): Line.this.Cell[_] = x match { case y: Cell[t] => y }

    var x: Line.this.Cell[_]Line.this.Cell[_] = new Cell(1)
    println({ x = new Cell("abc"); x })
  }
}

class FooW {
  class Line {
    case class Cell[T](var x: T)
    def f[T](x: Any): Cell[ _ ] = x match { case y: Cell[t] => y }

    var x: Cell[_] = new Cell(1)
    println({ x = new Cell("abc"); x })
  }
}

trait Counter[T] {
   def newCounter: T
   def get(i: T): Int
   def inc(i: T): T
}

case class C[T](x: T)

object LUB {
  def x = C(1)
  def y = C("abc")
  var coinflip: Boolean = _
  def z = if (coinflip) x else y
  def zz: C[_ >: Int with String] = z
  def zzs: C[_ >: Int with java.lang.String] = z
}

object Bug1189 {
  case class Cell[T](x: T)
  type U = Bug1189.Cell[_]
  def f(x: Any): U = x match { case y: Cell[_] => y }

  var x: U = Cell(1)
  println(x)

  println(f(x))

  x = Cell("abc")
  println(x)
  println(f(x))
}

object Test extends dotty.runtime.LegacyApp {

  val x = { class I; class J; (new C(new I), new C(new J)) }
  val y: (C[X], C[Y]) forSome { type X; type Y }(C[X], C[Y]) forSome { type X; type Y } = x

   def foo(x : Counter[T]{def name: String} forSome { type T }) = x match {
     case ctr: Counter[t] =>
       val c = ctr.newCounter
       println(ctr.name+" "+ctr.get(ctr.inc(ctr.inc(c))))
     case _ =>
   }

   def fooW(x : Counter[T]{def name: String} forSome { type T }) = x match {
     case ctr: Counter[t] =>
       val c = ctr.newCounter
       println(ctr.name+" "+ctr.get(ctr.inc(ctr.inc(c))))
     case _ =>
   }

   val ci = new Counter[Int] {
     def newCounter = 0
     def get(i: Int) = i
     def inc(i: Int) = i+1
     def name = "Int"
   }

   val cf = new Counter[Float] {
     def newCounter = 0
     def get(i: Float) = i.intValue
     def inc(i: Float) = i+1
     def name = "Float"
   }

   var ex: Counter[_]Counter[_] = _
   ex = ci
   ex = cf

   var exW: Counter[_] = _
   ex = ci
   ex = cf

   foo(ci)
   foo(cf)
   fooW(ci)
   fooW(cf)
   val foo = new Foo
   new foo.Line
   val fooW = new FooW
   new fooW.Line
}

trait FooBar[ A <: Option[_]] { def foo: A }
trait SubFooBar[B <: Option[_]] extends FooBar[B]

object Test1 {

  var pc: List[Product with (Product with Counter[T] forSome { 
  type T
})] = List()
  def f() = pc
  pc = f()
}
