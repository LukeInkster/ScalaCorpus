import language.existentials

object Test {
  class Row

  abstract class MyRelation [R <: Row, +Relation <: MyRelation[R, Relation]]

  type M = MyRelation[_ <: Row, _ <: MyRelation]

  val (x,y): (String, M) = null
}
