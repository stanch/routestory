package net.routestory.lounge2

import scala.concurrent._
import net.routestory.model2._
import java.util.concurrent.Executors
import scala.async.Async.{ async, await }

object Lounge {
  lazy val client = new FutureHttpClient
  val apiUrl = "https://routestory.herokuapp.com/api"
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

  implicit class OptionMap(map: Map[String, Option[String]]) {
    def ? = map.filter(_._2.isDefined).mapValues(_.get)
  }

  def story(id: String) = async {
    val story = await(client.getJson[Pillow[Story]](s"$apiUrl/stories/$id"))
    val author = await(client.getJson[Pillow[Author]](s"$apiUrl/authors/$id"))
    story.data.author = Some(author.data)
    story
  }

  private def addAuthors(stories: List[Puffy[StoryPreview]]) = async {
    val authorIds = stories.map(_.data.authorId)
    val authors = await(client.getJson[List[Pillow[Author]]](s"$apiUrl/authors/${authorIds.mkString(",")}"))
    (stories zip authors) foreach { case (s, a) ⇒ s.data.author = Some(a.data) }
    stories
  }

  def latestStories(limit: Int = 4, skip: Int = 0) = async {
    val stories = await(client.getJson[ViewResult[StoryPreview]](s"$apiUrl/stories/latest", Map(
      "limit" → limit.toString,
      "skip" → skip.toString
    )))
    await(addAuthors(stories.rows))
    stories
  }

  def searchStories(q: String, limit: Int = 4, bookmark: Option[String] = None) = async {
    val stories = await(client.getJson[SearchResult[StoryPreview]](s"$apiUrl/stories/search/$q", Map(
      "limit" → Some(limit.toString),
      "bookmark" → bookmark
    )?))
    await(addAuthors(stories.rows))
    stories
  }

  def taggedStories(tag: String, limit: Int = 4, bookmark: Option[String] = None) = async {
    val stories = await(client.getJson[SearchResult[StoryPreview]](s"$apiUrl/tags/$tag/stories", Map(
      "limit" → Some(limit.toString),
      "bookmark" → bookmark
    )?))
    await(addAuthors(stories.rows))
    stories
  }

  def author(id: String) = async {
    await(client.getJson[Pillow[Author]](s"$apiUrl/authors/$id"))
  }

  def popularTags = client.getJson[ReducedViewResult[String, Int]](s"$apiUrl/tags").map(_.rows)
}
