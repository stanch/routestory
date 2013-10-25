package net.routestory.bag

import spray.client.pipelining._

trait AuthorRoutes { self: RouteStoryService ⇒
  private val author = (path("(author-[A-Za-z0-9]+)".r) & get) { id ⇒
    couchComplete(Get(Couch.docUri(id)))
  }

  val authorRoutes = pathPrefix("authors") { author }
}
