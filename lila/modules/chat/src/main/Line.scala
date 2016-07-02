package lila.chat

import lila.user.User

import chess.Color

sealed trait Line {
  def text: String
  def author: String
  def deleted: Boolean
  def isSystem = author == systemUserId
  def isHuman = !isSystem
  def humanAuthor = isHuman option author
}

case class UserLine(
    username: String,
    text: String,
    troll: Boolean,
    deleted: Boolean) extends Line {

  def author = username

  def userId = User normalize username

  def delete = copy(deleted = true)
}
case class PlayerLine(
    color: Color,
    text: String) extends Line {
  def deleted = false
  def author = color.name
}

object Line {

  import lila.db.BSON
  import reactivemongo.bson.{ BSONHandler, BSONString }
  import org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4

  private val invalidLine = UserLine("", "[invalid character]", troll = false, deleted = true)

  def userLineBSONHandler(encoded: Boolean) = new BSONHandler[BSONString, UserLine] {
    def read(bsonStr: BSONString) = strToUserLine {
      if (encoded) unescapeHtml4(bsonStr.value) else bsonStr.value
    } | invalidLine
    def write(x: UserLine) = BSONString(userLineToStr(x))
  }

  def lineBSONHandler(encoded: Boolean) = new BSONHandler[BSONString, Line] {
    def read(bsonStr: BSONString) = strToLine {
      if (encoded) unescapeHtml4(bsonStr.value) else bsonStr.value
    } | invalidLine
    def write(x: Line) = BSONString(lineToStr(x))
  }

  private val UserLineRegex = """^([\w-]{2,})(.)(.+)$""".r
  def strToUserLine(str: String): Option[UserLine] = str match {
    case UserLineRegex(username, " ", text) => UserLine(username, text, troll = false, deleted = false).some
    case UserLineRegex(username, "!", text) => UserLine(username, text, troll = true, deleted = false).some
    case UserLineRegex(username, "?", text) => UserLine(username, text, troll = false, deleted = true).some
    case _                                  => none
  }
  def userLineToStr(x: UserLine) = {
    val sep = if (x.troll) "!"
    else if (x.deleted) "?"
    else " "
    s"${x.username}$sep${x.text}"
  }

  def strToLine(str: String): Option[Line] = strToUserLine(str) orElse {
    str.headOption flatMap Color.apply map { color =>
      PlayerLine(color, str drop 2)
    }
  }
  def lineToStr(x: Line) = x match {
    case u: UserLine   => userLineToStr(u)
    case p: PlayerLine => s"${p.color.letter} ${p.text}"
  }
}
