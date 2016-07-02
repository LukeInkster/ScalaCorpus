package views.html.chat

import play.api.libs.json.{ JsArray, Json }

import lila.common.PimpedJson._
import lila.api.Context
import lila.app.templating.Environment._

object ChatJsData {

  def json(chat: lila.chat.AnyChat, name: String, timeout: Boolean, withNote: Boolean = false, writeable: Boolean = true)(implicit ctx: Context) = Json.obj(
    "data" -> Json.obj(
      "id" -> chat.id,
      "name" -> name,
      "lines" -> lila.chat.JsonView(chat),
      "userId" -> ctx.userId,
      "loginRequired" -> chat.loginRequired
    ),
    "i18n" -> i18n(withNote = withNote),
    "writeable" -> writeable,
    "noteId" -> withNote.option(chat.id take 8),
    "kobold" -> ctx.troll,
    "permissions" -> Json.obj(
      "timeout" -> isGranted(_.ChatTimeout).option(true),
      "shadowban" -> isGranted(_.MarkTroll).option(true)).noNull,
    "timeout" -> timeout,
    "timeoutReasons" -> isGranted(_.ChatTimeout).option(lila.chat.JsonView.timeoutReasons)
  )

  def i18n(withNote: Boolean)(implicit ctx: Context) = i18nOptionJsObject(
    trans.talkInChat.some,
    trans.toggleTheChat.some,
    withNote option trans.notes,
    withNote option trans.typePrivateNotesHere
  )
}
