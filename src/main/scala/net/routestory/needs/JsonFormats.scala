package net.routestory.needs

import com.google.android.gms.maps.model.LatLng
import org.needs.Fulfillable
import play.api.libs.functional.syntax._
import play.api.libs.json._

import net.routestory.model._

// format: OFF

trait AuxiliaryFormats {
  implicit object latLngFormat extends Format[LatLng] {
    def reads(json: JsValue) = json match {
      case JsArray(Seq(JsNumber(lat), JsNumber(lng))) ⇒ JsSuccess(new LatLng(lat.toDouble, lng.toDouble))
      case _ ⇒ JsError()
    }
    def writes(latLng: LatLng) = Json.arr(latLng.latitude, latLng.longitude)
  }
}

trait MediaFormats extends AuxiliaryFormats {
  import Story._
  
  /* Locations */

  implicit val locationReads = Json.reads[Location]
  implicit object locationWrites extends Writes[Location] {
    def writes(loc: Location) = Json.obj(
      "timestamp" → loc.timestamp,
      "coordinates" → loc.coordinates,
      "type" → "Point"
    )
  }

  /* Media */

  implicit object mediaFormat extends Format[Media] {
    def reads(json: JsValue) = json \ "type" match {
      case JsString("sound") ⇒ json.validate[Sound]
      case JsString("voice-note") ⇒ json.validate[VoiceNote]
      case JsString("photo") ⇒ json.validate[Photo]
      case JsString("text-note") ⇒ json.validate[TextNote]
      case JsString("venue") ⇒ json.validate[Venue]
      case JsString("heartbeat") ⇒ json.validate[Heartbeat]
      case _ ⇒ json.validate[Unknown]
    }
    def writes(media: Media) = {
      val (j, t) = media match {
        case m @ Sound(_, _) ⇒ (soundFormat.writes(m), "sound")
        case m @ VoiceNote(_, _) ⇒ (voiceNoteFormat.writes(m), "voice-note")
        case m @ Photo(_, _) ⇒ (photoFormat.writes(m), "photo")
        case m @ TextNote(_, _) ⇒ (textNoteFormat.writes(m), "text-note")
        case m @ Venue(_, _, _, _) ⇒ (venueFormat.writes(m), "venue")
        case m @ Heartbeat(_, _) ⇒ (heartBeatFormat.writes(m), "heartbeat")
        case m @ Unknown(_, tp, _) ⇒ (unknownWrites.writes(m), tp)
      }
      j.as[JsObject] ++ Json.obj("type" → t)
    }
  }

  implicit val unknownReads = (
    (__ \ 'timestamp).read[Int] and
    (__ \ 'type).read[String] and
    __.read[JsValue]
  )(Unknown.apply _)

  implicit val unknownWrites = __.write[JsValue].contramap[Unknown](_.raw)

  implicit val soundFormat = Json.format[Sound]
  implicit val voiceNoteFormat = Json.format[VoiceNote]
  implicit val photoFormat = Json.format[Photo]
  implicit val textNoteFormat = Json.format[TextNote]
  implicit val venueFormat = Json.format[Venue]
  implicit val heartBeatFormat = Json.format[Heartbeat]
  implicit val chapterFormat = Json.format[Chapter]
  implicit val metaFormat = Json.format[Meta]
}

object SavingFormats extends MediaFormats {
  implicit val storyWrites = (
    (__ \ '_id).write[String] and
    (__ \ 'meta).write[Story.Meta] and
    (__ \ 'chapters).write[List[Story.Chapter]] and
    (__ \ 'authorId).write[Option[String]].contramap { a: Option[Author] ⇒ a.map(_.id) } and
    (__ \ 'private).write[Boolean]
  )(unlift(Story.unapply))
}

trait LoadingFormats extends MediaFormats { self: Shared with Needs ⇒
  /* Story */

  implicit def storyReads = Fulfillable.reads[Story] {
    (__ \ '_id).read[String] and
    (__ \ 'meta).read[Story.Meta] and
    (__ \ 'chapters).read[List[Story.Chapter]] and
    (__ \ 'authorId).read[Option[String]].map(_.map(NeedAuthor)).map(Fulfillable.jumpOption) and
    (__ \ 'private).read[Boolean]
  }

  /* Story preview */

  object storyPreviewReadsLatest {
    implicit def storyPreviewReads = Fulfillable.reads[StoryPreview] {
      (__ \ 'id).read[String] and
      (__ \ 'value \ 'title).read[Option[String]] and
      (__ \ 'value \ 'tags).read[List[String]] and
      (__ \ 'value \ 'authorId).read[Option[String]].map(_.map(NeedAuthor)).map(Fulfillable.jumpOption)
    }
  }

  object storyPreviewReadsSearched {
    implicit def storyPreviewReads = Fulfillable.reads[StoryPreview] {
      (__ \ 'id).read[String] and
      (__ \ 'fields \ 'title).read[Option[String]] and
      ((__ \ 'fields \ 'tags).read[List[String]] orElse (__ \ 'fields \ 'tags).read[String].map(_ :: Nil)) and
      (__ \ 'fields \ 'authorId).read[Option[String]].map(_.map(NeedAuthor)).map(Fulfillable.jumpOption)
    }
  }

  /* Author */

  implicit val authorReads = Fulfillable.reads[Author] {
    (__ \ '_id).read[String] and
    (__ \ 'name).read[String] and
    (__ \ 'link).read[Option[String]] and
    (__ \ 'picture).read[Option[String]]
  }

  /* Collections */

  implicit def latestReads = Fulfillable.reads[Latest] {
    import storyPreviewReadsLatest._
    (__ \ 'total_rows).read[Int] and
    (__ \ 'rows).read[List[Fulfillable[StoryPreview]]].map(Fulfillable.jumpList)
  }

  implicit def searchedReads = Fulfillable.reads[Searched] {
    import storyPreviewReadsSearched._
    (__ \ 'total_rows).read[Int] and
    (__ \ 'bookmark).read[String] and
    (__ \ 'rows).read[List[Fulfillable[StoryPreview]]].map(Fulfillable.jumpList)
  }

  implicit val tagReads = (
    (__ \ 'key).read[String] and
    (__ \ 'value).read[Int]
  )(Tag.apply _)

  implicit val manyTagsReads = (__ \ 'rows).read[List[Tag]]
}
