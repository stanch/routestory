package net.routestory.bag

import spray.client.pipelining._

trait TagRoutes { self: RouteStoryService ⇒
  private val alltags = path(PathEnd) {
    proxyPass(Get("/story2/_design/Story/_view/tags?reduce=true&group=true"))
  }

  val tagRoutes = pathPrefix("tags") {
    get {
      alltags
    }
  }
}
