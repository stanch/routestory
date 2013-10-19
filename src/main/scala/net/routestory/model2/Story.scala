package net.routestory.model2

import play.api.libs.json._
import play.api.libs.functional.syntax._

object Story {
  trait Timed {
    val timestamp: Int
  }

  case class Location(timestamp: Int, coordinates: List[Double]) extends Timed
  object Location {
    implicit val reads = Json.reads[Location]
    implicit val writes = new Writes[Location] {
      def writes(loc: Location) = Json.obj(
        "timestamp" → Json.toJson(loc.timestamp),
        "coordinates" → Json.toJson(loc.coordinates),
        "type" → "Point"
      )
    }
  }

  trait Media extends Timed {
    val url: String
  }

  case class Audio(timestamp: Int, url: String) extends Media
  object Audio {
    implicit val format = Json.format[Audio]
  }

  case class Image(timestamp: Int, url: String) extends Media
  object Image {
    implicit val format = Json.format[Image]
  }

  case class TextNote(timestamp: Int, text: String) extends Timed
  object TextNote {
    implicit val format = Json.format[TextNote]
  }

  case class Venue(timestamp: Int, id: String, name: String, coordinates: List[Double]) extends Timed
  object Venue {
    implicit val format = Json.format[Venue]
  }

  case class Heartbeat(timestamp: Int, bpm: Int) extends Timed
  object Heartbeat {
    implicit val format = Json.format[Heartbeat]
  }

  case class Segment(
    start: Long,
    duration: Int,
    locations: List[Location],
    photos: List[Image],
    sounds: List[Audio],
    textNotes: List[TextNote],
    voiceNotes: List[Audio],
    venues: List[Venue],
    heartbeat: List[Heartbeat])
  object Segment {
    implicit val format = Json.format[Segment]
  }

  case class Meta(title: Option[String], description: Option[String], tags: List[String] = Nil)
  object Meta {
    implicit val format = Json.format[Meta]
  }

  implicit val format = Json.format[Story]
}

case class Story(meta: Story.Meta, segments: List[Story.Segment], authorId: Option[String], `private`: Boolean = true) {
  var author: Option[Author] = None
}

case class StoryPreview(title: Option[String], tags: List[String], authorId: String)
object StoryPreview {
  implicit val read = (
    (__ \ 'title).read[Option[String]] and
    ((__ \ 'tags).read[List[String]] orElse (__ \ "tags").read[String].map(_ :: Nil)) and
    (__ \ 'authorId).read[String]
  )(apply _)
}