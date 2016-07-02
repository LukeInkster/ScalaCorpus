package lila.mod

import lila.db.dsl._

final class ModlogApi(coll: Coll) {

  import lila.db.BSON.BSONJodaDateTimeHandler
  private implicit val ModlogBSONHandler = reactivemongo.bson.Macros.handler[Modlog]

  def streamConfig(mod: String) = add {
    Modlog(mod, none, Modlog.streamConfig)
  }

  def engine(mod: String, user: String, v: Boolean) = add {
    Modlog(mod, user.some, v.fold(Modlog.engine, Modlog.unengine))
  }

  def booster(mod: String, user: String, v: Boolean) = add {
    Modlog(mod, user.some, v.fold(Modlog.booster, Modlog.unbooster))
  }

  def troll(mod: String, user: String, v: Boolean) = add {
    Modlog(mod, user.some, v.fold(Modlog.troll, Modlog.untroll))
  }

  def ban(mod: String, user: String, v: Boolean) = add {
    Modlog(mod, user.some, v.fold(Modlog.ipban, Modlog.ipunban))
  }

  def closeAccount(mod: String, user: String) = add {
    Modlog(mod, user.some, Modlog.closeAccount)
  }

  def reopenAccount(mod: String, user: String) = add {
    Modlog(mod, user.some, Modlog.reopenAccount)
  }

  def setTitle(mod: String, user: String, title: Option[String]) = add {
    val name = title flatMap lila.user.User.titlesMap.get
    Modlog(mod, user.some, name.isDefined.fold(Modlog.setTitle, Modlog.removeTitle), details = name)
  }

  def setEmail(mod: String, user: String) = add {
    Modlog(mod, user.some, Modlog.setEmail)
  }

  def ipban(mod: String, ip: String) = add {
    Modlog(mod, none, Modlog.ipban, ip.some)
  }

  def deletePost(mod: String, user: Option[String], author: Option[String], ip: Option[String], text: String) = add {
    Modlog(mod, user, Modlog.deletePost, details = Some(
      author.??(_ + " ") + ip.??(_ + " ") + text.take(140)
    ))
  }

  def toggleCloseTopic(mod: String, categ: String, topic: String, closed: Boolean) = add {
    Modlog(mod, none, closed ? Modlog.closeTopic | Modlog.openTopic, details = Some(
      categ + " / " + topic
    ))
  }

  def toggleHideTopic(mod: String, categ: String, topic: String, hidden: Boolean) = add {
    Modlog(mod, none, hidden ? Modlog.hideTopic | Modlog.showTopic, details = Some(
      categ + " / " + topic
    ))
  }

  def deleteQaQuestion(mod: String, user: String, title: String) = add {
    Modlog(mod, user.some, Modlog.deleteQaQuestion, details = Some(title take 140))
  }

  def deleteQaAnswer(mod: String, user: String, text: String) = add {
    Modlog(mod, user.some, Modlog.deleteQaAnswer, details = Some(text take 140))
  }

  def deleteQaComment(mod: String, user: String, text: String) = add {
    Modlog(mod, user.some, Modlog.deleteQaComment, details = Some(text take 140))
  }

  def deleteTeam(mod: String, name: String, desc: String) = add {
    Modlog(mod, none, Modlog.deleteTeam, details = s"$name / $desc".take(200).some)
  }

  def terminateTournament(mod: String, name: String) = add {
    Modlog(mod, none, Modlog.terminateTournament, details = name.some)
  }

  def chatTimeout(mod: String, user: String, reason: String) = add {
    Modlog(mod, user.some, Modlog.chatTimeout, details = reason.some)
  }

  def recent = coll.find($empty).sort($sort naturalDesc).cursor[Modlog]().gather[List](100)

  def wasUnengined(userId: String) = coll.exists($doc(
    "user" -> userId,
    "action" -> Modlog.unengine
  ))

  def wasUnbooster(userId: String) = coll.exists($doc(
    "user" -> userId,
    "action" -> Modlog.unbooster
  ))

  def userHistory(userId: String): Fu[List[Modlog]] =
    coll.find($doc("user" -> userId)).sort($sort desc "date").cursor[Modlog]().gather[List](100)

  private def add(m: Modlog): Funit = {
    lila.mon.mod.log.create()
    lila.log("mod").info(m.toString)
    coll.insert(m).void
  }
}
