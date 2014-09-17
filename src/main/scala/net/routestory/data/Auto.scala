package net.routestory.data

import scala.concurrent.{ExecutionContext, Future}
import net.routestory.external._

object Auto {
  def autoChapter(chapter: Story.Chapter, foursquareApi: foursquare.Api)(implicit ec: ExecutionContext): Future[Story.Chapter] = {
    val elements = Future.sequence(chapter.elements.map(_.timestamp) map { timestamp ⇒
      val location = chapter.locationAt(timestamp)
      foursquareApi.nearbyVenues(location, 100).go map { venues ⇒
        Timed(timestamp, venues.head)
      }
    })
    elements.map(e ⇒ chapter.withElements(e))
  }
}
