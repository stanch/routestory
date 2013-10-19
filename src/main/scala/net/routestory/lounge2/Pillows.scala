package net.routestory.lounge2

import play.api.libs.json._
import play.api.libs.functional.syntax._

trait Puffy[A] {
  val id: String
  val data: A
}

case class Pillow[A: Format](id: String, data: A, `type`: String, attachments: Option[JsValue] = None, rev: Option[String] = None) extends Puffy[A]
object Pillow {
  implicit def format[A: Format] = (
    (__ \ '_id).format[String] and
    (__).format[A] and
    (__ \ 'type).format[String] and
    (__ \ '_attachments).format[Option[JsValue]] and
    (__ \ '_rev).format[Option[String]]
  )(apply[A], unlift(unapply[A]))
}

case class ReducedViewResult[K: Reads, A: Reads](rows: List[ReducedViewResult.Pad[K, A]])
object ReducedViewResult {
  case class Pad[K: Reads, A: Reads](key: K, data: A)
  object Pad {
    implicit def read[K: Reads, A: Reads] = (
      (__ \ 'key).read[K] and
      (__ \ 'value).read[A]
    )(apply[K, A] _)
  }

  implicit def read[K: Reads, A: Reads] = (__ \ 'rows).read[List[ReducedViewResult.Pad[K, A]]].map(apply[K, A])
}

case class ViewResult[A: Reads](total: Int, offset: Int, rows: List[ViewResult.Pad[A]]) {
  lazy val data = rows.map(_.data)
}
object ViewResult {
  case class Pad[A: Reads](id: String, key: JsValue, data: A) extends Puffy[A]
  object Pad {
    implicit def read[A: Reads] = (
      (__ \ 'id).read[String] and
      (__ \ 'key).read[JsValue] and
      (__ \ 'value).read[A]
    )(apply[A] _)
  }

  implicit def read[A: Reads] = (
    (__ \ 'total_rows).read[Int] and
    (__ \ 'offset).read[Int] and
    (__ \ 'rows).read[List[Pad[A]]]
  )(apply[A] _)
}

case class SearchResult[A: Reads](total: Int, bookmark: String, rows: List[SearchResult.Pad[A]]) {
  lazy val data = rows.map(_.data)
}
object SearchResult {
  case class Pad[A: Reads](id: String, order: JsValue, data: A) extends Puffy[A]
  object Pad {
    implicit def read[A: Reads] = (
      (__ \ 'id).read[String] and
      (__ \ 'order).read[JsValue] and
      (__ \ 'fields).read[A]
    )(apply[A] _)
  }

  implicit def read[A: Reads] = (
    (__ \ 'total_rows).read[Int] and
    (__ \ 'bookmark).read[String] and
    (__ \ 'rows).read[List[Pad[A]]]
  )(apply[A] _)
}