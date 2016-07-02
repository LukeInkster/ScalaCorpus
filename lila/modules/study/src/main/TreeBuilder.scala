package lila.study

import chess.format.{ Forsyth, Uci, UciCharPair }
import chess.opening._
import lila.socket.tree

import play.api.libs.json._

object TreeBuilder {

  private type Ply = Int
  private type OpeningOf = String => Option[FullOpening]

  def apply(root: Node.Root) = tree.Root(
    ply = root.ply,
    fen = root.fen.value,
    check = root.check,
    shapes = root.shapes,
    comments = root.comments,
    glyphs = root.glyphs,
    crazyData = root.crazyData,
    children = toBranches(root.children),
    opening = FullOpeningDB findByFen root.fen.value)

  def toBranch(node: Node): tree.Branch = tree.Branch(
    id = node.id,
    ply = node.ply,
    move = node.move,
    fen = node.fen.value,
    check = node.check,
    shapes = node.shapes,
    comments = node.comments,
    glyphs = node.glyphs,
    crazyData = node.crazyData,
    children = toBranches(node.children),
    opening = FullOpeningDB findByFen node.fen.value)

  private def toBranches(children: Node.Children) =
    children.nodes.toList.map(toBranch)
}
