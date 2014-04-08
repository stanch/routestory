package net.routestory.needs

import com.google.android.gms.maps.model.LatLng
import org.needs.Resolvable
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.data.mapping.json.Rules._
import play.api.data.mapping.json.Writes._

import net.routestory.model._
import play.api.data.mapping._
import play.api.libs.functional.FunctionalBuilder
import scala.concurrent.Future
import java.io.File
import play.api.data.mapping.json.Rules

// format: OFF

trait AuxiliaryFormats {
  implicit val latLngRule = Rule[JsValue, LatLng] {
    case JsArray(Seq(JsNumber(lat), JsNumber(lng))) ⇒ Success(new LatLng(lat.toDouble, lng.toDouble))
    case _ ⇒ Failure(Seq())
  }

  implicit val latLngWrite = Write[LatLng, JsValue] { latLng ⇒
    Json.arr(latLng.latitude, latLng.longitude)
  }
}

trait MediaReads extends AuxiliaryFormats { self: Needs ⇒
  import Story._
  
  implicit val locationRule = Rule.gen[JsValue, Location]

  val unknownMediaRule = Resolvable.rule[JsValue, UnknownMedia] { __ ⇒
    (__ \ "timestamp").read[Int] and
    (__ \ "type").read[String] and
    __.read[JsObject]
  }.fmap(x ⇒ x: Resolvable[Media])

  val heavyMediaRuleBuilder = { __ : Reader[JsValue] ⇒
    (__ \ "timestamp").read[Int] and
    (__ \ "url").read[String] and
    (__ \ "url").read[String].fmap(media).fmap(Resolvable.defer)
  }

  def mediaTypeRule[A <: Resolvable[Media]](tp: String)(rule: Rule[JsValue, A]) = From[JsValue] { __ ⇒
    (__ \ "type").read(Rules.equalTo(tp)) ~> rule.fmap(x ⇒ x: Resolvable[Media])
  }

  val soundRule = mediaTypeRule("sound")(Resolvable.rule[JsValue, Sound](heavyMediaRuleBuilder))
  val voiceNoteRule = mediaTypeRule("voice-note")(Resolvable.rule[JsValue, VoiceNote](heavyMediaRuleBuilder))
  val photoRule = mediaTypeRule("photo")(Resolvable.rule[JsValue, Photo](heavyMediaRuleBuilder))
  val textNoteRule = mediaTypeRule("text-note")(Resolvable.pureRule(Rule.gen[JsValue, TextNote]))
  val venueRule = mediaTypeRule("venue")(Resolvable.pureRule(Rule.gen[JsValue, Venue]))
  val heartbeatRule = mediaTypeRule("heartbeat")(Resolvable.pureRule(Rule.gen[JsValue, Heartbeat]))

  implicit val mediaRule =
    soundRule orElse
    voiceNoteRule orElse
    photoRule orElse
    textNoteRule orElse
    venueRule orElse
    heartbeatRule orElse
    unknownMediaRule

  implicit val metaRule = Rule.gen[JsValue, Meta]
}

trait MediaWrites extends AuxiliaryFormats {
  import Story._

  implicit val locationWrite = Write.gen[Location, JsObject].map(_ + ("type" → JsString("Point")))

  val unknownMediaWrite = Write.zero[JsObject].contramap { x: UnknownMedia ⇒ x.raw }

  def heavyMediaWrite[A <: HeavyMedia] = Write[A, JsObject] { m ⇒
    Json.obj(
      "timestamp" → m.timestamp,
      "url" → m.url
    )
  }

  val soundWrite = heavyMediaWrite[Sound]
  val voiceNoteWrite = heavyMediaWrite[VoiceNote]
  val photoWrite = heavyMediaWrite[Photo]
  val textNoteWrite = Write.gen[TextNote, JsObject]
  val venueWrite = Write.gen[Venue, JsObject]
  val heartbeatWrite = Write.gen[Heartbeat, JsObject]

  implicit val metaWrite = Write.gen[Meta, JsObject]

  implicit val mediaWrite = Write[Media, JsObject] { media ⇒
    val (j, t) = media match {
      case m @ Sound(_, _, _) ⇒ (soundWrite.writes(m), "sound")
      case m @ VoiceNote(_, _, _) ⇒ (voiceNoteWrite.writes(m), "voice-note")
      case m @ Photo(_, _, _) ⇒ (photoWrite.writes(m), "photo")
      case m @ TextNote(_, _) ⇒ (textNoteWrite.writes(m), "text-note")
      case m @ Venue(_, _, _, _) ⇒ (venueWrite.writes(m), "venue")
      case m @ Heartbeat(_, _) ⇒ (heartbeatWrite.writes(m), "heartbeat")
      case m @ UnknownMedia(_, tp, _) ⇒ (unknownMediaWrite.writes(m), tp)
    }
    j ++ Json.obj("type" → t)
  }
}

object SavingFormats extends MediaWrites {
  import Story._

  implicit val chapterWrite = Write.gen[Chapter, JsObject]

  implicit val storyWrite = To[JsObject] { __ ⇒
    ((__ \ "_id").write[String] and
    (__ \ "meta").write[Meta] and
    (__ \ "chapters").write[List[Chapter]] and
    (__ \ "authorId").write[Option[String]].contramap { a: Option[Author] ⇒ a.map(_.id) } and
    (__ \ "private").write[Boolean])(unlift(Story.unapply))
  }
}

trait LoadingFormats extends MediaReads { self: Shared with Needs ⇒
  import Story._

  /* Chapter */

  implicit val chapterRule: Rule[JsValue, Resolvable[Chapter]] = Resolvable.rule[JsValue, Chapter] { __ ⇒
    (__ \ "start").read[Long] and
    (__ \ "duration").read[Int] and
    (__ \ "locations").read[List[Location]] and
    (__ \ "media").read[List[Resolvable[Media]]].fmap(Resolvable.fromList)
  }

  /* Story */

  implicit val storyRule = Resolvable.rule[JsValue, Story] { __ ⇒
    (__ \ "_id").read[String] and
    (__ \ "meta").read[Meta] and
    (__ \ "chapters").read[List[Resolvable[Chapter]]].fmap(Resolvable.fromList) and
    (__ \ "authorId").read[Option[String]].fmap(_.map(author)).fmap(Resolvable.fromOption) and
    (__ \ "private").read[Boolean]
  }

  /* Story preview */

  object storyPreviewReadsLatest {
    implicit val storyPreviewRule = Resolvable.rule[JsValue, StoryPreview] { __ ⇒
      (__ \ "id").read[String] and
      (__ \ "value" \ "title").read[Option[String]] and
      (__ \ "value" \ "tags").read[List[String]] and
      (__ \ "value" \ "authorId").read[Option[String]].fmap(_.map(author)).fmap(Resolvable.fromOption)
    }
  }

  object storyPreviewReadsSearched {
    implicit val storyPreviewRule = Resolvable.rule[JsValue, StoryPreview] { __ ⇒
      (__ \ "id").read[String] and
      (__ \ "fields" \ "title").read[Option[String]] and
      ((__ \ "fields" \ "tags").read[List[String]] orElse (__ \ "fields" \ "tags").read[String].fmap(_ :: Nil)) and
      (__ \ "fields" \ "authorId").read[Option[String]].fmap(_.map(author)).fmap(Resolvable.fromOption)
    }
  }

  /* Author */

  implicit val authorRule = Resolvable.rule[JsValue, Author] { __ ⇒
    (__ \ "_id").read[String] and
    (__ \ "name").read[String] and
    (__ \ "link").read[Option[String]] and
    (__ \ "picture").read[Option[String]].fmap(_.map(media).map(Resolvable.defer)).fmap(Resolvable.fromOption)
  }

  /* Collections */

  implicit val latestRule = Resolvable.rule[JsValue, Latest] { __ ⇒
    import storyPreviewReadsLatest._
    (__ \ "total_rows").read[Int] and
    (__ \ "rows").read[List[Resolvable[StoryPreview]]].fmap(Resolvable.fromList)
  }

  implicit val searchedRule = Resolvable.rule[JsValue, Searched] { __ ⇒
    import storyPreviewReadsSearched._
    (__ \ "total_rows").read[Int] and
    (__ \ "bookmark").read[String] and
    (__ \ "rows").read[List[Resolvable[StoryPreview]]].fmap(Resolvable.fromList)
  }

  implicit val tagRule: Rule[JsValue, Resolvable[Tag]] = Resolvable.rule[JsValue, Tag] { __ ⇒
    (__ \ "key").read[String] and
    (__ \ "value").read[Int]
  }
}
