package lila.study

import lila.user.User

case class Settings(
    computer: Settings.UserSelection,
    explorer: Settings.UserSelection) {

}

object Settings {

  val init = Settings(
    computer = UserSelection.Everyone,
    explorer = UserSelection.Everyone)

  sealed trait UserSelection {
    lazy val key = toString.toLowerCase
  }
  object UserSelection {
    case object Everyone extends UserSelection
    case object Nobody extends UserSelection
    case object Owner extends UserSelection
    case object Contributor extends UserSelection
    val byKey = List(Everyone, Nobody, Owner, Contributor).map { v => v.key -> v }.toMap

    def allows(sel: UserSelection, study: Study, userId: Option[User.ID]): Boolean = sel match {
      case Everyone    => true
      case Nobody      => false
      case Owner       => userId ?? study.isOwner
      case Contributor => userId ?? study.canContribute
    }
  }
}
