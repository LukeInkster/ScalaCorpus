object Labels {
  def main(args: Array[String]): Unit = {
  var i = 10
  while(i>0) {
   var j = 0
   while(j<i) {
   println(j +" " + i)
   j = j + 1
   }
   i = i - 1}
   pattern(1)
   pattern(2)
   pattern(3)
 }

 def pattern(a: Int) = a match {
  case 1 if (a>0) => println("one")
  case t@2 => println("two" + t)
  case _ => println("default")
 }
}
