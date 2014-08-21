package net.routestory.json

import com.javadocmd.simplelatlng.LatLng
import net.routestory.data.{Timed, Author, Story}
import net.routestory.data.Story._
import play.api.data.mapping.{To, Write}
import play.api.libs.json._
import play.api.data.mapping.json.Writes._
import play.api.libs.functional.syntax._

trait AuxiliaryWrites {
  implicit val latLngWrite = Write[LatLng, JsObject] { latLng ⇒
    Json.obj(
      "type" → "Point",
      "coordinates" → List(latLng.getLatitude, latLng.getLongitude)
    )
  }

  implicit def timedWrite[A](implicit write: Write[A, JsObject]) = Write[Timed[A], JsObject] { timed ⇒
    write.writes(timed.data) ++ Json.obj("timestamp" → timed.timestamp)
  }
}

trait ElementWrites extends AuxiliaryWrites {
  val unknownElementWrite = Write.zero[JsObject].contramap { x: UnknownElement ⇒ x.raw }

  def mediaElementWrite[A <: MediaElement] = Write[A, JsObject] { m ⇒
    Json.obj(
      "url" → m.url
    )
  }

  val soundWrite = mediaElementWrite[Sound]
  val voiceNoteWrite = mediaElementWrite[VoiceNote]
  val photoWrite = mediaElementWrite[Photo]
  val flickrPhotoWrite = Write[FlickrPhoto, JsObject] { m ⇒
    Json.obj(
      "id" → m.id,
      "title" → m.title,
      "url" → m.url
    )
  }
  val instagramPhotoWrite = Write[InstagramPhoto, JsObject] { m ⇒
    Json.obj(
      "id" → m.id,
      "title" → m.title,
      "url" → m.url
    )
  }
  val textNoteWrite = Write.gen[TextNote, JsObject]
  val foursquareVenueWrite = Write.gen[FoursquareVenue, JsObject]

  implicit val metaWrite = Write.gen[Meta, JsObject]

  implicit val elementWrite = Write[Element, JsObject] { element ⇒
    val (j, t) = element match {
      case m @ Sound(_, _) ⇒ (soundWrite.writes(m), "sound")
      case m @ VoiceNote(_, _) ⇒ (voiceNoteWrite.writes(m), "voice-note")
      case m @ Photo(_, _) ⇒ (photoWrite.writes(m), "photo")
      case m @ FlickrPhoto(_, _, _, _) ⇒ (flickrPhotoWrite.writes(m), "flickr-photo")
      case m @ InstagramPhoto(_, _, _, _) ⇒ (instagramPhotoWrite.writes(m), "instagram-photo")
      case m @ TextNote(_) ⇒ (textNoteWrite.writes(m), "text-note")
      case m @ FoursquareVenue(_, _, _) ⇒ (foursquareVenueWrite.writes(m), "foursquare-venue")
      case m @ UnknownElement(tp, _) ⇒ (unknownElementWrite.writes(m), tp)
    }
    j ++ Json.obj("type" → t)
  }
}

object JsonWrites extends ElementWrites {
  implicit val chapterWrite = To[JsObject] { __ ⇒
    ((__ \ "start").write[Long] and
     (__ \ "duration").write[Int] and
     (__ \ "locations").write[List[Timed[LatLng]]].contramap { (_: Vector[Timed[LatLng]]).toList } and
     (__ \ "media").write[List[Timed[Element]]].contramap { (_: Vector[Timed[Element]]).toList })(unlift(Chapter.unapply))
  }

  implicit val storyWrite = To[JsObject] { __ ⇒
    ((__ \ "_id").write[String] and
     (__ \ "meta").write[Meta] and
     (__ \ "chapters").write[List[Chapter]] and
     (__ \ "authorId").write[Option[String]].contramap { a: Option[Author] ⇒ a.map(_.id) } and
     (__ \ "private").write[Boolean])(unlift(Story.unapply))
  }
}
