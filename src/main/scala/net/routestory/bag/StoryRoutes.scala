package net.routestory.bag

import spray.client.pipelining._
import spray.http.Uri
import spray.http.Uri.Query

trait StoryRoutes { self: RouteStoryService ⇒
  private val latest = (path("latest") & parameters('limit.as[Int] ?, 'skip.as[Int] ?)) { (limit, skip) ⇒
    proxyPass(Get(Uri.from(
      path = "/story2/_design/Story/_view/byTimestamp",
      query = Query(
        "descending" → "true",
        "limit" → limit.getOrElse(10).toString,
        "skip" → skip.getOrElse(0).toString
      )
    )))
  }

  private val tagged = path("tags" / Segment) { tag ⇒
    proxyPass(Get(Lucene.uri("q" → Lucene.exact("tags", tag))))
  }

  private val search = path("search" / Segment) { key ⇒
    proxyPass(Get(Lucene.uri("q" → (Lucene.fuzzy("tags", key) + Lucene.fuzzy("title", key)))))
  }

  private val story = pathPrefix("(story-[A-Za-z0-9]+)".r) { id ⇒
    get {
      path(PathEnd) {
        proxyPass(Get(s"/story2/$id"))
      } ~
      path(Rest) { attachmentId ⇒
        proxyPass(Get(s"/story2/$id/$attachmentId"))
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
