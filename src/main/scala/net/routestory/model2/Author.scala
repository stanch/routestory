package net.routestory.model2

import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.needs._

// format: OFF

case class Author(id: String, name: String, link: Option[String], picture: Option[String]) extends org.needs.rest.HasId

object Author {
  implicit val reads = Fulfillable.reads[Author] {
    (__ \ '_id).read[String] and
    (__ \ 'name).read[String] and
    (__ \ 'link).read[Option[String]] and
    (__ \ 'picture).read[Option[String]]
  }
}
