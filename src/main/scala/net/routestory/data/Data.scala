package net.routestory.data

import java.io.File
import com.javadocmd.simplelatlng.util.LengthUnit
import play.api.libs.json.JsObject
import scala.concurrent.Future
import com.javadocmd.simplelatlng.{LatLngTool, LatLng}

case class Timed[+A](timestamp: Int, data: A) {
  override def toString = s"${timestamp}s -> $data"
}

object Story {
  sealed trait Element

  sealed trait ExternalElement extends Element

  case class UnknownElement(`type`: String, raw: JsObject) extends Element

  sealed trait KnownElement extends Element

  sealed trait MediaElement extends KnownElement {
    def url: String
    def data: Future[File]
    def withFile(file: File): MediaElement
  }

  sealed trait Audio extends MediaElement
  case class Sound(url: String, data: Future[File]) extends Audio {
    def withFile(file: File) = Sound(file.getPath, Future.successful(file))
  }
  object Sound {
    def apply(file: File) = new Sound(file.getAbsolutePath, Future.successful(file))
  }
  case class VoiceNote(url: String, data: Future[File]) extends Audio {
    def withFile(file: File) = VoiceNote(file.getPath, Future.successful(file))
  }
  object VoiceNote {
    def apply(file: File) = new VoiceNote(file.getAbsolutePath, Future.successful(file))
  }

  sealed trait Image extends MediaElement
  case class Photo(url: String, data: Future[File]) extends Image {
    def withFile(file: File) = Photo(file.getPath, Future.successful(file))
  }
  object Photo {
    def apply(file: File) = new Photo(file.getAbsolutePath, Future.successful(file))
  }
  case class FlickrPhoto(id: String, title: String, url: String, data: Future[File]) extends Image with ExternalElement {
    def withFile(file: File) = FlickrPhoto(id, title, file.getPath, Future.successful(file))
  }

  case class TextNote(text: String) extends KnownElement

  case class FoursquareVenue(id: String, name: String, coordinates: LatLng) extends KnownElement with ExternalElement

  type Route = Vector[Timed[LatLng]]

  case class Chapter(start: Long, duration: Int, locations: Route, elements: Vector[Timed[Element]]) {
    lazy val distance = (locations zip locations.drop(1)).map {
      case (Timed(_, ll1), Timed(_, ll2)) ⇒ LatLngTool.distance(ll1, ll2, LengthUnit.METER)
    }.sum

    lazy val knownElements = elements flatMap {
      case x @ Timed(_, e: KnownElement) ⇒ Vector(x.copy(data = e))
      case _ ⇒ Vector.empty
    }

    lazy val effectiveDuration = Math.max(
      if (locations.nonEmpty) locations.maxBy(_.timestamp).timestamp else 0,
      if (elements.nonEmpty) elements.maxBy(_.timestamp).timestamp else 0
    )

    def withElement(element: Timed[Element]) = copy(elements = elements :+ element)
    def withLocation(location: Timed[LatLng]) = copy(locations = locations :+ location)
    def finish = copy(duration = ts)

    def ts = (System.currentTimeMillis / 1000 - start).toInt

    def location[A](timed: Timed[A]) = locationAt(timed.timestamp)

    def locationAt(time: Double, route: Route = locations): LatLng = {
      route.span(_.timestamp < time) match {
        case (Vector(), Vector()) ⇒ null
        case (Vector(), l2 +: after) ⇒ l2.data
        case (before :+ l1, Vector()) ⇒ l1.data
        case (before :+ l1, l2 +: after) ⇒
          val t = (time - l1.timestamp) / (l2.timestamp - l1.timestamp)
          def i(x: Double, y: Double) = x + t * (y - x)
          new LatLng(
            i(l1.data.getLatitude, l2.data.getLatitude),
            i(l1.data.getLongitude, l2.data.getLongitude)
          )
      }
    }
  }

  object Chapter {
    def empty = Chapter(System.currentTimeMillis / 1000, 0, Vector.empty, Vector.empty)
  }

  case class Meta(title: Option[String], description: Option[String], tags: List[String])

  object Meta {
    def fromStrings(title: String, description: String, tags: String) = new Meta(
      Option(title).filter(_.nonEmpty),
      Option(description).filter(_.nonEmpty),
      tags.split(",\\s*").filter(_.nonEmpty).toList
    )
    def empty = Meta(None, None, Nil)
  }

  def empty(id: String) = Story(id, Meta.empty, Nil, None)
}

case class Story(id: String, meta: Story.Meta, chapters: List[Story.Chapter], author: Option[Author], `private`: Boolean = true) {
  def withChapter(chapter: Story.Chapter) = copy(chapters = chapters :+ chapter)
  def withMeta(meta: Story.Meta) = copy(meta = meta)

  def preview = StoryPreview(id, meta.title, meta.tags, author)
}

case class StoryPreview(id: String, title: Option[String], tags: List[String], author: Option[Author])

case class Author(id: String, name: String, link: Option[String], picture: Option[Future[File]])

case class Tag(tag: String, count: Int)

case class Latest(total: Int, stories: List[StoryPreview])

case class Searched(total: Int, bookmark: String, stories: List[StoryPreview])
