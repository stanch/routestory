package net.routestory.bag

import spray.client.pipelining._

trait TagRoutes { self: RouteStoryService ⇒
  private val alltags = path(PathEnd) {
    couchComplete(Get(Couch.viewUri("Story", "tags", "reduce" → "true", "group" → "true")))
  }

  val tagRoutes = pathPrefix("tags") {
    get {
      alltags
    }
  }
}
