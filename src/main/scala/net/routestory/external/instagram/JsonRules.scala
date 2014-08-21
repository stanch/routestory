package net.routestory.external.instagram

import java.io.File

import net.routestory.data.Story
import play.api.data.mapping.From
import play.api.data.mapping.json.Rules._
import play.api.libs.json.JsValue
import resolvable.Resolvable

trait JsonRules {
  def media(url: String): Resolvable[File]

  def readUrl = From[JsValue] { __ ⇒
    (__ \ "images" \ "low_resolution" \ "url").read[String]
  }

  implicit val photoRule = Resolvable.rule[JsValue, Story.InstagramPhoto] { __ ⇒
    (__ \ "id").read[String] and
    (__ \ "title").read[Option[String]] and
    readUrl and
    readUrl.fmap(media).fmap(Resolvable.defer)
  }
}
