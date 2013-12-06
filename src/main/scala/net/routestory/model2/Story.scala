package net.routestory.model2

import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.needs._
import net.routestory.needs.NeedAuthor

// format: OFF

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

  case class Chapter(
    start: Long,
    duration: Int,
    locations: List[Location],
    photos: List[Image],
    sounds: List[Audio],
    textNotes: List[TextNote],
    voiceNotes: List[Audio],
    venues: List[Venue],
    heartbeat: List[Heartbeat])
  object Chapter {
    implicit val format = Json.format[Chapter]
  }

  case class Meta(title: Option[String], description: Option[String], tags: List[String] = Nil)
  object Meta {
    implicit val format = Json.format[Meta]
  }

  implicit val reads = Fulfillable.reads[Story] {
    (__ \ '_id).read[String] and
    (__ \ 'meta).read[Story.Meta] and
    (__ \ 'segments).read[List[Story.Chapter]] and
    (__ \ 'authorId).read[Option[String]].map(_.map(NeedAuthor)).map(Fulfillable.jumpOption) and
    (__ \ 'private).read[Boolean]
  }
}

case class Story(
  id: String,
  meta: Story.Meta,
  chapters: List[Story.Chapter],
  author: Option[Author],
  `private`: Boolean = true) extends org.needs.rest.HasId

