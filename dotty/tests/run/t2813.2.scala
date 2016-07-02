import java.util.LinkedList
import collection.JavaConversions._

object Test extends dotty.runtime.LegacyApp {
  def assertListEquals[A](expected: List[A], actual: Seq[A]): Unit = {
    assert(expected.sameElements(actual),
           "Expected list to contain " + expected.mkString("[", ", ", "]") +
           ", but was " + actual.mkString("[", ", ", "]"))
  }

  def addAllOfNonCollectionWrapperAtZeroOnEmptyLinkedList(): Unit = {
    val l = new LinkedList[Int]
    l.addAll(0, List(1, 2))
    assertListEquals(List(1, 2), l)
  }

  def addAllOfNonCollectionWrapperAtZeroOnLinkedList(): Unit = {
    val l = new LinkedList[Int] += 1 += 2
    l.addAll(0, List(10, 11))
    assertListEquals((List(10, 11, 1, 2)), l)
  }

  def addAllOfCollectionWrapperAtZeroOnLinkedList(): Unit = {
    val l = new LinkedList[Int] += 1 += 2
    l.addAll(0, new LinkedList[Int] += 10 += 11)
    assertListEquals((List(10, 11, 1, 2)), l)
  }

  def addAllOfCollectionWrapperAtZeroOnEmptyLinkedList(): Unit = {
    val l = new LinkedList[Int]
    l.addAll(0, new LinkedList[Int] += 10 += 11)
    assertListEquals((List(10, 11)), l)
  }

  addAllOfNonCollectionWrapperAtZeroOnEmptyLinkedList
  addAllOfNonCollectionWrapperAtZeroOnLinkedList
  addAllOfCollectionWrapperAtZeroOnEmptyLinkedList
  addAllOfCollectionWrapperAtZeroOnLinkedList
}
