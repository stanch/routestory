package net.routestory.bag

trait TagRoutes { self: RouteStoryService ⇒
  val tagRoutes = (pathPrefix("tags") & get) {
    pathEnd {
      couchComplete(Couch.viewReq("Story", "tags", "reduce" → "true", "group" → "true"))
    } ~
    path(Segment / "stories") { tag ⇒
      couchComplete(Couch.searchReq("Story", "byEverything", "q" → Lucene.exact("tags", tag)))
    }
  }
}
