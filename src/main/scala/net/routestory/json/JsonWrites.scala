package net.routestory.json

import com.javadocmd.simplelatlng.LatLng
import net.routestory.data.Author
import net.routestory.data.Story
import net.routestory.data.Story._
import play.api.data.mapping.{To, Write}
import play.api.libs.json._
import play.api.data.mapping.json.Writes._
import play.api.libs.functional.syntax._

trait AuxiliaryWrites {
  implicit val latLngWrite = Write[LatLng, JsValue] { latLng ⇒
    Json.arr(latLng.getLatitude, latLng.getLongitude)
  }
}

trait ElementWrites extends AuxiliaryWrites {
  implicit val locationWrite = Write.gen[Location, JsObject].map(_ + ("type" → JsString("Point")))

  val unknownElementWrite = Write.zero[JsObject].contramap { x: UnknownElement ⇒ x.raw }

  def mediaElementWrite[A <: MediaElement] = Write[A, JsObject] { m ⇒
    Json.obj(
      "timestamp" → m.timestamp,
      "url" → m.url
    )
  }

  val soundWrite = mediaElementWrite[Sound]
  val voiceNoteWrite = mediaElementWrite[VoiceNote]
  val photoWrite = mediaElementWrite[Photo]
  val textNoteWrite = Write.gen[TextNote, JsObject]
  val venueWrite = Write.gen[Venue, JsObject]
  val heartbeatWrite = Write.gen[Heartbeat, JsObject]

  implicit val metaWrite = Write.gen[Meta, JsObject]

  implicit val mediaWrite = Write[Element, JsObject] { media ⇒
    val (j, t) = media match {
      case m @ Sound(_, _, _) ⇒ (soundWrite.writes(m), "sound")
      case m @ VoiceNote(_, _, _) ⇒ (voiceNoteWrite.writes(m), "voice-note")
      case m @ Photo(_, _, _) ⇒ (photoWrite.writes(m), "photo")
      case m @ TextNote(_, _) ⇒ (textNoteWrite.writes(m), "text-note")
      case m @ Venue(_, _, _, _) ⇒ (venueWrite.writes(m), "venue")
      case m @ Heartbeat(_, _) ⇒ (heartbeatWrite.writes(m), "heartbeat")
      case m @ UnknownElement(_, tp, _) ⇒ (unknownElementWrite.writes(m), tp)
    }
    j ++ Json.obj("type" → t)
  }
}

object JsonWrites extends ElementWrites {
  implicit val chapterWrite = To[JsObject] { __ ⇒
    ((__ \ "start").write[Long] and
     (__ \ "duration").write[Int] and
     (__ \ "locations").write[List[Location]] and
     (__ \ "media").write[List[Element]])(unlift(Chapter.unapply))
  }

  implicit val storyWrite = To[JsObject] { __ ⇒
    ((__ \ "_id").write[String] and
     (__ \ "meta").write[Meta] and
     (__ \ "chapters").write[List[Chapter]] and
     (__ \ "authorId").write[Option[String]].contramap { a: Option[Author] ⇒ a.map(_.id) } and
     (__ \ "private").write[Boolean])(unlift(Story.unapply))
  }
}
