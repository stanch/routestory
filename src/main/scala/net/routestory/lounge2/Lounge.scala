package net.routestory.lounge2

import scala.concurrent._
import net.routestory.model2._
import java.util.concurrent.Executors
import scala.async.Async.{ async, await }

object Lounge {
  lazy val client = new FutureHttpClient
  val apiUrl = "https://routestory.herokuapp.com/api"
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

  def getStory(id: String) = async {
    val story = await(client.getJson[Pillow[Story]](s"$apiUrl/stories/$id"))
    val author = await(client.getJson[Pillow[Author]](s"$apiUrl/authors/$id"))
    story.data.author = Some(author.data)
    story
  }

  def getPopularTags = client.getJson[ReducedViewResult[String, Int]](s"$apiUrl/tags/").map(_.rows)
}
