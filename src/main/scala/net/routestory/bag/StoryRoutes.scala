package net.routestory.bag

import spray.client.pipelining._
import spray.http.Uri
import spray.http.Uri.Query

trait StoryRoutes { self: RouteStoryService ⇒
  private val latest = (path("latest") & parameters('limit.as[Int] ?, 'skip.as[Int] ?)) { (limit, skip) ⇒
    couchComplete(Get(Couch.viewUri("Story", "byTimestamp",
      "descending" → "true",
      "limit" → limit.getOrElse(10).toString,
      "skip" → skip.getOrElse(0).toString
    )))
  }

  private val tagged = path("tags" / Segment) { tag ⇒
    couchComplete(Get(Lucene.uri("q" → Lucene.exact("tags", tag))))
  }

  private val search = path("search" / Segment) { key ⇒
    couchComplete(Get(Lucene.uri("q" → (Lucene.fuzzy("tags", key) + Lucene.fuzzy("title", key)))))
  }

  private val story = pathPrefix("(story-[A-Za-z0-9]+)".r) { id ⇒
    get {
      path(PathEnd) {
        couchComplete(Get(Couch.docUri(id)))
      } ~
      path(Rest) { attachmentId ⇒
        couchComplete(Get(Couch.attUri(id, attachmentId)))
      }
    }
  }

  val storyRoutes = pathPrefix("stories") {
    get {
      latest ~ tagged ~ search
    } ~
    story
  }
}
