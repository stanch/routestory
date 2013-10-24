package net.routestory.bag

import spray.client.pipelining._

trait AuthorRoutes { self: RouteStoryService ⇒
  private val author = (path("(author-[A-Za-z0-9]+)".r) & get) { id ⇒
    proxyPass(Get(s"/story2/$id"))
  }

  val authorRoutes = pathPrefix("authors") { author }
}
