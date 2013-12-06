package net.routestory.model2

import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.needs._
import net.routestory.needs.NeedAuthor

// format: OFF

case class StoryPreview(id: String, title: Option[String], tags: List[String], author: Option[Author])
object StoryPreview {
  implicit val read = Fulfillable.reads[StoryPreview] {
    (__ \ 'id).read[String] and
    (__ \ 'value \ 'title).read[Option[String]] and
    ((__ \ 'value \ 'tags).read[List[String]] orElse (__ \ 'value \ 'tags).read[String].map(_ :: Nil)) and
    (__ \ 'value \ 'authorId).read[Option[String]].map(_.map(NeedAuthor)).map(Fulfillable.jumpOption)
  }
}

case class Latest(total: Int, stories: List[StoryPreview])
object Latest {
  implicit val reads = Fulfillable.reads[Latest] {
    (__ \ 'total_rows).read[Int] and
    (__ \ 'rows).read[List[Fulfillable[StoryPreview]]].map(Fulfillable.jumpList)
  }
}

case class Searched(total: Int, bookmark: String, stories: List[StoryPreview])
object Searched {
  implicit val reads = Fulfillable.reads[Searched] {
    (__ \ 'total_rows).read[Int] and
    (__ \ 'bookmark).read[String] and
    (__ \ 'rows).read[List[Fulfillable[StoryPreview]]].map(Fulfillable.jumpList)
  }
}

case class Tag(tag: String, count: Int)
object Tag {
  implicit val reads = (
    (__ \ 'key).read[String] and
    (__ \ 'value).read[Int]
  )(Tag.apply _)

  implicit val readsMany = (__ \ 'rows).read[List[Tag]]
}