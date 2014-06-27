package net.routestory.data

import java.io.File
import com.javadocmd.simplelatlng.util.LengthUnit
import play.api.libs.json.JsObject
import scala.concurrent.Future
import com.javadocmd.simplelatlng.{LatLngTool, LatLng}

object Story {
  sealed trait Timed {
    val timestamp: Int
  }

  case class Location(timestamp: Int, coordinates: LatLng) extends Timed

  sealed trait Element extends Timed {
    def location(implicit chapter: Chapter) = chapter.locationAt(timestamp)
  }

  case class UnknownElement(timestamp: Int, `type`: String, raw: JsObject) extends Element

  sealed trait KnownElement extends Element

  sealed trait MediaElement extends KnownElement {
    def url: String
    def data: Future[File]
    def withFile(file: File): MediaElement
  }

  sealed trait Audio extends MediaElement
  case class Sound(timestamp: Int, url: String, data: Future[File]) extends Audio {
    def withFile(file: File) = Sound(timestamp, file.getPath, Future.successful(file))
  }
  case class VoiceNote(timestamp: Int, url: String, data: Future[File]) extends Audio {
    def withFile(file: File) = VoiceNote(timestamp, file.getPath, Future.successful(file))
  }

  sealed trait Image extends MediaElement
  case class Photo(timestamp: Int, url: String, data: Future[File]) extends Image {
    def withFile(file: File) = Photo(timestamp, file.getPath, Future.successful(file))
  }

  case class TextNote(timestamp: Int, text: String) extends KnownElement

  case class Venue(timestamp: Int, id: String, name: String, coordinates: LatLng) extends KnownElement

  case class Heartbeat(timestamp: Int, bpm: Int) extends KnownElement

  case class Chapter(start: Long, duration: Int, locations: List[Location], elements: List[Element]) {
    lazy val distance = (locations zip locations.drop(1)).map {
      case (Location(_, ll1), Location(_, ll2)) ⇒ LatLngTool.distance(ll1, ll2, LengthUnit.METER)
    }.sum

    def locationAt(time: Double): LatLng = {
      locations.span(_.timestamp < time) match {
        case (Nil, Nil) ⇒ null
        case (Nil, l2 :: after) ⇒ l2.coordinates
        case (before, Nil) ⇒ before.last.coordinates
        case (before, l2 :: after) ⇒
          val l1 = before.last
          val t = (time - l1.timestamp) / (l2.timestamp - l1.timestamp)
          def i(x: Double, y: Double) = x + t * (y - x)
          new LatLng(
            i(l1.coordinates.getLatitude, l2.coordinates.getLatitude),
            i(l1.coordinates.getLongitude, l2.coordinates.getLongitude)
          )
      }
    }
  }

  case class Meta(title: Option[String], description: Option[String], tags: List[String] = Nil)
}

case class Story(id: String, meta: Story.Meta, chapters: List[Story.Chapter], author: Option[Author], `private`: Boolean = true)

case class StoryPreview(id: String, title: Option[String], tags: List[String], author: Option[Author])

case class Author(id: String, name: String, link: Option[String], picture: Option[Future[File]])

case class Tag(tag: String, count: Int)

case class Latest(total: Int, stories: List[StoryPreview])

case class Searched(total: Int, bookmark: String, stories: List[StoryPreview])
