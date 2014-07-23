package net.routestory.external.flickr

import java.io.File

import net.routestory.data.Story
import play.api.data.mapping.From
import play.api.data.mapping.json.Rules._
import play.api.libs.json.JsValue
import resolvable.Resolvable

trait JsonRules {
  def media(url: String): Resolvable[File]

  def readUrl = From[JsValue] { __ ⇒
    ((__ \ "farm").read[Int] and
     (__ \ "server").read[String] and
     (__ \ "id").read[String] and
     (__ \ "secret").read[String]
    )((farm, server, id, secret) ⇒ s"http://farm$farm.staticflickr.com/$server/${id}_$secret.jpg")
  }

  implicit val photoRule = Resolvable.rule[JsValue, Story.FlickrPhoto] { __ ⇒
    (__ \ "id").read[String] and
    (__ \ "title").read[String] and
    readUrl and
    readUrl.fmap(media).fmap(Resolvable.defer)
  }
}
