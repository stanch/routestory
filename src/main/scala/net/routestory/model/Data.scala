package net.routestory.model

import com.google.android.gms.maps.model.LatLng
import play.api.libs.json.JsValue
import scala.concurrent.ExecutionContext
import android.content.Context
import net.routestory.needs.NeedMedia

object Story {
  sealed trait Timed {
    val timestamp: Int
  }

  case class Location(timestamp: Int, coordinates: LatLng) extends Timed

  sealed trait Media extends Timed {
    def location(implicit chapter: Chapter) = chapter.locationAt(timestamp)
  }

  sealed trait ExternalMedia extends Media {
    val url: String
  }

  implicit class DownloadableExternalMedia(m: ExternalMedia)(implicit ctx: Context, ec: ExecutionContext) {
    def fetch = NeedMedia(m.url).go
  }

  sealed trait Audio extends ExternalMedia
  case class Sound(timestamp: Int, url: String) extends Audio
  case class VoiceNote(timestamp: Int, url: String) extends Audio

  sealed trait Image extends ExternalMedia
  case class Photo(timestamp: Int, url: String) extends Image

  case class TextNote(timestamp: Int, text: String) extends Media

  case class Venue(timestamp: Int, id: String, name: String, coordinates: LatLng) extends Media

  case class Heartbeat(timestamp: Int, bpm: Int) extends Media {
    def vibrationPattern(times: Int): Array[Long] = {
      val p_wave = 80L
      val t_wave = 100L
      val short_interval = 150L
      val beat_interval = 60 * 1000L / bpm
      val long_interval = Math.max(beat_interval - short_interval - p_wave - t_wave, 0)
      val pattern = List(p_wave, short_interval) ::: List.fill(99)(1L) ::: long_interval :: Nil
      (0L :: List.fill(Math.max(times, 1))(pattern).flatten).toArray
    }
  }

  case class Unknown(timestamp: Int, `type`: String, raw: JsValue) extends Media

  case class Chapter(start: Long, duration: Int, locations: List[Location], media: List[Media]) {
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
            i(l1.coordinates.latitude, l2.coordinates.latitude),
            i(l1.coordinates.longitude, l2.coordinates.longitude)
          )
      }
    }
  }

  case class Meta(title: Option[String], description: Option[String], tags: List[String] = Nil)
}

case class Story(id: String, meta: Story.Meta, chapters: List[Story.Chapter], author: Option[Author], `private`: Boolean = true)

case class StoryPreview(id: String, title: Option[String], tags: List[String], author: Option[Author])

case class Author(id: String, name: String, link: Option[String], picture: Option[String])
