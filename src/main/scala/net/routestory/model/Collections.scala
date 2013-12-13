package net.routestory.model

import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.needs._
import JsonFormats._

// format: OFF

case class Latest(total: Int, stories: List[StoryPreview])
object Latest {
  import storyPreviewReadsLatest._
  implicit val reads = Fulfillable.reads[Latest] {
    (__ \ 'total_rows).read[Int] and
    (__ \ 'rows).read[List[Fulfillable[StoryPreview]]].map(Fulfillable.jumpList)
  }
}

case class Searched(total: Int, bookmark: String, stories: List[StoryPreview])
object Searched {
  import storyPreviewReadsSearched._
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