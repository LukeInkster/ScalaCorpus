package lila.report

import org.joda.time.DateTime
import ornicar.scalalib.Random

import lila.user.User

case class Report(
    _id: String, // also the url slug
    user: String, // the reportee
    reason: String,
    text: String,
    processedBy: Option[String],
    createdAt: DateTime,
    createdBy: String) {

  def id = _id
  def slug = _id

  def isCreator(user: String) = user == createdBy

  def isCheat = realReason == Reason.Cheat
  def isOther = realReason == Reason.Other
  def isTrollOrInsult = realReason == Reason.Troll || realReason == Reason.Insult

  def unprocessedCheat = unprocessed && isCheat
  def unprocessedOther = unprocessed && isOther
  def unprocessedTrollOrInsult = unprocessed && isTrollOrInsult

  def isCommunication = Reason.communication contains realReason

  def isAutomatic = createdBy == "lichess"
  def isManual = !isAutomatic

  def process(by: User) = copy(processedBy = by.id.some)

  def unprocessed = processedBy.isEmpty

  lazy val realReason: Reason = Reason byName reason
}

object Report {

  case class WithUser(report: Report, user: User)

  def make(
    user: User,
    reason: Reason,
    text: String,
    createdBy: User): Report = new Report(
    _id = Random nextStringUppercase 8,
    user = user.id,
    reason = reason.name,
    text = text,
    processedBy = none,
    createdAt = DateTime.now,
    createdBy = createdBy.id)
}
