package lila.forum

import lila.common.paginator._
import lila.db.dsl._
import lila.db.paginator._
import lila.user.{ User, UserContext }

private[forum] final class CategApi(env: Env) {

  import BSONHandlers._

  def list(teams: Set[String], troll: Boolean): Fu[List[CategView]] = for {
    categs ← CategRepo withTeams teams
    views ← (categs map { categ =>
      env.postApi get (categ lastPostId troll) map { topicPost =>
        CategView(categ, topicPost map {
          _ match {
            case (topic, post) => (topic, post, env.postApi lastPageOf topic)
          }
        }, troll)
      }
    }).sequenceFu
  } yield views

  def teamNbPosts(slug: String): Fu[Int] = CategRepo nbPosts teamSlug(slug)

  def makeTeam(slug: String, name: String): Funit =
    CategRepo.nextPosition flatMap { position =>
      val categ = Categ(
        _id = teamSlug(slug),
        name = name,
        desc = "Forum of the team " + name,
        pos = position,
        team = slug.some,
        nbTopics = 0,
        nbPosts = 0,
        lastPostId = "",
        nbTopicsTroll = 0,
        nbPostsTroll = 0,
        lastPostIdTroll = "")
      val topic = Topic.make(
        categId = categ.slug,
        slug = slug + "-forum",
        name = name + " forum",
        troll = false,
        featured = true)
      val post = Post.make(
        topicId = topic.id,
        author = none,
        userId = "lichess".some,
        ip = none,
        text = "Welcome to the %s forum!\nOnly members of the team can post here, but everybody can read." format name,
        number = 1,
        troll = false,
        hidden = topic.hidden,
        lang = "en".some,
        categId = categ.id)
      env.categColl.insert(categ).void >>
        env.postColl.insert(post).void >>
        env.topicColl.insert(topic withPost post).void >>
        env.categColl.update($id(categ.id), categ withTopic post).void
    }

  def show(slug: String, page: Int, troll: Boolean): Fu[Option[(Categ, Paginator[TopicView])]] =
    optionT(CategRepo bySlug slug) flatMap { categ =>
      optionT(env.topicApi.paginator(categ, page, troll) map { (categ, _).some })
    }

  def denormalize(categ: Categ): Funit = for {
    topics ← TopicRepo byCateg categ
    topicIds = topics map (_.id)
    nbPosts ← PostRepo countByTopics topicIds
    lastPost ← PostRepo lastByTopics topicIds
    topicsTroll ← TopicRepoTroll byCateg categ
    topicIdsTroll = topicsTroll map (_.id)
    nbPostsTroll ← PostRepoTroll countByTopics topicIdsTroll
    lastPostTroll ← PostRepoTroll lastByTopics topicIdsTroll
    _ ← env.categColl.update($id(categ.id), categ.copy(
      nbTopics = topics.size,
      nbPosts = nbPosts,
      lastPostId = lastPost ?? (_.id),
      nbTopicsTroll = topicsTroll.size,
      nbPostsTroll = nbPostsTroll,
      lastPostIdTroll = lastPostTroll ?? (_.id)
    )).void
  } yield ()
}
