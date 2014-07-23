package net.routestory.json

import java.io.File

import com.javadocmd.simplelatlng.LatLng
import net.routestory.data._
import net.routestory.data.Story._
import play.api.data.mapping._
import play.api.data.mapping.json.Rules
import play.api.data.mapping.json.Rules._
import play.api.libs.json._
import resolvable.Resolvable

// format: OFF

trait AuxiliaryRules {
  implicit val latLngRule = From[JsValue] { __ ⇒
    ((__ \ "coordinates").read[List[Double]] and
     (__ \ "type").read(Rules.equalTo("Point")))((l, _) ⇒ new LatLng(l(0), l(1)))
  }

  implicit def timedRule[A](implicit rule: Rule[JsValue, A]) = From[JsValue] { __ ⇒
    ((__ \ "timestamp").read[Int] and __.read[A])((t, d) ⇒ Timed(t, d))
  }

  implicit def resolvableTimedRule[A](implicit rule: Rule[JsValue, Resolvable[A]]): Rule[JsValue, Resolvable[Timed[A]]] =
    timedRule[Resolvable[A]].fmap(t ⇒ t.data.map(d ⇒ t.copy(data = d)))
}

trait ElementRules extends AuxiliaryRules {
  def media(url: String): Resolvable[File]

  val unknownElementRule = Resolvable.rule[JsValue, UnknownElement] { __ ⇒
    (__ \ "type").read[String] and
    __.read[JsObject]
  }.fmap(x ⇒ x: Resolvable[Element])

  val mediaElementRuleBuilder = { __ : Reader[JsValue] ⇒
    (__ \ "url").read[String] and
    (__ \ "url").read[String].fmap(media).fmap(Resolvable.defer)
  }

  def elementTypeRule[A <: Resolvable[Element]](tp: String)(rule: Rule[JsValue, A]) = From[JsValue] { __ ⇒
    (__ \ "type").read(Rules.equalTo(tp)) ~> rule.fmap(x ⇒ x: Resolvable[Element])
  }

  val soundRule = elementTypeRule("sound")(Resolvable.rule[JsValue, Sound](mediaElementRuleBuilder))
  val voiceNoteRule = elementTypeRule("voice-note")(Resolvable.rule[JsValue, VoiceNote](mediaElementRuleBuilder))
  val photoRule = elementTypeRule("photo")(Resolvable.rule[JsValue, Photo](mediaElementRuleBuilder))
  val flickrPhotoRule = elementTypeRule("flickr-photo")(Resolvable.rule[JsValue, FlickrPhoto] { __ : Reader[JsValue] ⇒
    (__ \ "id").read[String] and
    (__ \ "title").read[String] and
    (__ \ "url").read[String] and
    (__ \ "url").read[String].fmap(media).fmap(Resolvable.defer)
  })
  val textNoteRule = elementTypeRule("text-note")(Resolvable.pureRule(Rule.gen[JsValue, TextNote]))
  val foursquareVenueRule = elementTypeRule("foursquare-venue")(Resolvable.pureRule(Rule.gen[JsValue, FoursquareVenue]))

  implicit val elementRule =
    soundRule orElse
    voiceNoteRule orElse
    photoRule orElse
    flickrPhotoRule orElse
    textNoteRule orElse
    foursquareVenueRule orElse
    unknownElementRule

  implicit val metaRule = Rule.gen[JsValue, Meta]
}

trait JsonRules extends ElementRules {
  def author(id: String): Resolvable[Author]

  /* Chapter */

  implicit val chapterRule: Rule[JsValue, Resolvable[Chapter]] = Resolvable.rule[JsValue, Chapter] { __ ⇒
    (__ \ "start").read[Long] and
    (__ \ "duration").read[Int] and
    (__ \ "locations").read[List[Timed[LatLng]]].fmap(_.toVector) and
    (__ \ "media").read[List[Resolvable[Timed[Element]]]].fmap(Resolvable.fromList).fmap(_.map(_.toVector.sortBy(_.timestamp)))
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
