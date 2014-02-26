package net.routestory.model

import com.google.android.gms.maps.model.LatLng
import play.api.libs.json.JsValue
import scala.concurrent.{ Future, ExecutionContext }
import java.io.File
import net.routestory.needs.BitmapPool

object Story {
  sealed trait Timed {
    val timestamp: Int
  }

  case class Location(timestamp: Int, coordinates: LatLng) extends Timed

  sealed trait Media extends Timed {
    def location(implicit chapter: Chapter) = chapter.locationAt(timestamp)
  }

  sealed trait KnownMedia extends Media

  sealed trait HeavyMedia extends KnownMedia {
    val url: String
    val data: Future[File]
  }

  sealed trait Audio extends HeavyMedia
  case class Sound(timestamp: Int, url: String, data: Future[File]) extends Audio
  case class VoiceNote(timestamp: Int, url: String, data: Future[File]) extends Audio

  sealed trait Image extends HeavyMedia
  case class Photo(timestamp: Int, url: String, data: Future[File]) extends Image

  case class TextNote(timestamp: Int, text: String) extends KnownMedia

  case class Venue(timestamp: Int, id: String, name: String, coordinates: LatLng) extends KnownMedia

  case class Heartbeat(timestamp: Int, bpm: Int) extends KnownMedia

  case class UnknownMedia(timestamp: Int, `type`: String, raw: JsValue) extends Media

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

object MediaOps {
  implicit class ImageOps(image: Story.Image) {
    def bitmap(maxSize: Int)(implicit ec: ExecutionContext) =
      image.data.map(BitmapPool.get(maxSize)).collect { case b if b != null ⇒ b }
  }

  implicit class HeartbeatOps(heartbeat: Story.Heartbeat) {
    def vibrationPattern(times: Int): Array[Long] = {
      val p_wave = 80L
      val t_wave = 100L
      val short_interval = 150L
      val beat_interval = 60 * 1000L / heartbeat.bpm
      val long_interval = Math.max(beat_interval - short_interval - p_wave - t_wave, 0)
      val pattern = List(p_wave, short_interval) ::: List.fill(99)(1L) ::: long_interval :: Nil
      (0L :: List.fill(Math.max(times, 1))(pattern).flatten).toArray
    }
  }

  implicit class AuthorOps(author: Author) {
    def bitmap(maxSize: Int)(implicit ec: ExecutionContext) =
      author.picture.map(_.map(BitmapPool.get(maxSize)).collect { case b if b != null ⇒ b })
  }
}

case class Story(id: String, meta: Story.Meta, chapters: List[Story.Chapter], author: Option[Author], `private`: Boolean = true)

case class StoryPreview(id: String, title: Option[String], tags: List[String], author: Option[Author])

case class Author(id: String, name: String, link: Option[String], picture: Option[Future[File]])

case class Tag(tag: String, count: Int)

case class Latest(total: Int, stories: List[StoryPreview])

case class Searched(total: Int, bookmark: String, stories: List[StoryPreview])
