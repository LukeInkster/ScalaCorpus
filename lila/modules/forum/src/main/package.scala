package lila

package object forum extends PackageObject with WithPlay {

  private[forum] def teamSlug(id: String) = "team-" + id

  private[forum] val logger = lila.log("forum")
}
