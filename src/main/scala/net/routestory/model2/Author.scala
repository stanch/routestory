package net.routestory.model2

import play.api.libs.json._

case class Author(name: String, pictureUrl: Option[String])
object Author {
  implicit val format = Json.format[Author]
}
