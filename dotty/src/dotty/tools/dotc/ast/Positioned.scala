package dotty.tools.dotc
package ast

import util.Positions._
import util.DotClass

/** A base class for things that have positions (currently: modifiers and trees)
 */
abstract class Positioned extends DotClass with Product {

  private[this] var curPos: Position = _

  setPos(initialPos)

  /** The item's position.
   */
  def pos: Position = curPos

  /** Destructively update `curPos` to given position. Also, set any missing
   *  positions in children.
   */
  protected def setPos(pos: Position): Unit = {
    curPos = pos
    if (pos.exists) setChildPositions(pos.toSynthetic)
  }

  /** The envelope containing the item in its entirety. Envelope is different from
   *  `pos` for definitions (instances of MemberDef).
   */
  def envelope: Position = pos.toSynthetic

  /** A positioned item like this one with the position set to `pos`.
   *  if the positioned item is source-derived, a clone is returned.
   *  If the positioned item is synthetic, the position is updated
   *  destructively and the item itself is returned.
   */
  def withPos(pos: Position): this.type = {
    val newpd = (if (pos == curPos || curPos.isSynthetic) this else clone).asInstanceOf[Positioned]
    newpd.setPos(pos)
    newpd.asInstanceOf[this.type]
  }

  def withPos(posd: Positioned): this.type =
    if (posd == null) this else withPos(posd.pos)

  /** This item with a position that's the union of the given `pos` and the
   *  current position.
   */
  def addPos(pos: Position): this.type = withPos(pos union this.pos)

  /** Set position of this tree only, without performing
   *  any checks of consistency with - or updates of - other positions.
   *  Called from Unpickler when entering positions.
   */
  private[dotc] def setPosUnchecked(pos: Position) = curPos = pos

  /** If any children of this node do not have positions,
   *  fit their positions between the positions of the known subtrees
   *  and transitively visit their children.
   *  The method is likely time-critical because it is invoked on any node
   *  we create, so we want to avoid object allocations in the common case.
   *  The method is naturally expressed as two mutually (tail-)recursive
   *  functions, one which computes the next element to consider or terminates if there
   *  is none and the other which propagates the position information to that element.
   *  But since mutual tail recursion is not supported in Scala, we express it instead
   *  as a while loop with a termination by return in the middle.
   */
  private def setChildPositions(pos: Position): Unit = {
    var n = productArity                    // subnodes are analyzed right to left
    var elems: List[Any] = Nil              // children in lists still to be considered, from right to left
    var end = pos.end                       // the last defined offset, fill in positions up to this offset
    var outstanding: List[Positioned] = Nil // nodes that need their positions filled once a start position
                                            // is known, from left to right.
    def fillIn(ps: List[Positioned], start: Int, end: Int): Unit = ps match {
      case p :: ps1 =>
        p.setPos(Position(start, end))
        fillIn(ps1, end, end)
      case nil =>
    }
    while (true) {
      var nextChild: Any = null // the next child to be considered
      if (elems.nonEmpty) {
        nextChild = elems.head
        elems = elems.tail
      }
      else if (n > 0) {
        n = n - 1
        nextChild = productElement(n)
      }
      else {
        fillIn(outstanding, pos.start, end)
        return
      }
      nextChild match {
        case p: Positioned =>
          if (p.pos.exists) {
            fillIn(outstanding, p.pos.end, end)
            outstanding = Nil
            end = p.pos.start
          }
          else outstanding = p :: outstanding
        case xs: List[_] =>
          elems = elems ::: xs.reverse
        case _ =>
      }
    }
  }

  /** The initial, synthetic position. This is usually the union of all positioned children's
   *  envelopes.
   */
  protected def initialPos: Position = {
    var n = productArity
    var pos = NoPosition
    while (n > 0) {
      n -= 1
      productElement(n) match {
        case p: Positioned => pos = pos union p.envelope
        case xs: List[_] => pos = unionPos(pos, xs)
        case _ =>
      }
    }
    pos.toSynthetic
  }

  private def unionPos(pos: Position, xs: List[_]): Position = xs match {
    case (p: Positioned) :: xs1 => unionPos(pos union p.envelope, xs1)
    case _ => pos
  }

  def contains(that: Positioned): Boolean = {
    def isParent(x: Any): Boolean = x match {
      case x: Positioned =>
        x contains that
      case xs: List[_] =>
        xs exists isParent
      case _ =>
        false
    }
    (this eq that) ||
      (this.envelope contains that.pos) && {
        var n = productArity
        var found = false
        while (n > 0 && !found) {
          n -= 1
          found = isParent(productElement(n))
        }
        found
      }
  }
}
