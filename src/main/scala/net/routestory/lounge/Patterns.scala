package net.routestory.lounge

import scala.async.Async.{ async, await }
import net.routestory.model._
import scala.concurrent.ExecutionContext

object Patterns {
  def getStory(lounge: Lounge)(id: String)(implicit ec: ExecutionContext) = async {
    val story = await(lounge.getObject[Story](id))
    val author = if (story.authorId != null) await(lounge.getObject[Author](story.authorId)) else null
    story.author = author
    story
  }
}
