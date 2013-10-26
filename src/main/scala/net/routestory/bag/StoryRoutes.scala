package net.routestory.bag

import spray.client.pipelining._
import spray.http.Uri
import spray.http.Uri.Query

trait StoryRoutes { self: RouteStoryService ⇒
  private val storyId = Shortuuid.matcher("story")

  private val latest = (path("latest") & parameters('limit.as[Int] ?, 'skip.as[Int] ?)) { (limit, skip) ⇒
    couchComplete(Couch.viewReq("Story", "byTimestamp",
      "descending" → "true",
      "limit" → limit.getOrElse(10).toString,
      "skip" → skip.getOrElse(0).toString
    ))
  }

  private val search = (path("search" / Segment) & parameters('limit.as[Int] ?, 'bookmark.as[String] ?)) { (key, limit, bookmark) ⇒ ctx ⇒
    val args = Seq(
      "limit" → limit.getOrElse(10).toString,
      "q" → (Lucene.fuzzy("tags", key) + Lucene.fuzzy("title", key))
    ) ++ bookmark.map(b ⇒ Seq("bookmark" → b)).getOrElse(Seq())
    couchComplete(Couch.searchReq("Story", "byEverything", args: _*))(ctx)
  }

  private val story = pathPrefix(storyId) { id ⇒
    get {
      path(PathEnd) {
        couchComplete(Get(Couch.docUri(id)))
      } ~
      path(Rest) { attachmentId ⇒
        couchComplete(Couch.attReq(id, attachmentId))
      }
    }
  }

  val storyRoutes = pathPrefix("stories") {
    get(latest ~ search) ~ story
  }
}
