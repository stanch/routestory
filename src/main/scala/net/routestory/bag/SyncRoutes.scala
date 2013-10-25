package net.routestory.bag

import spray.client.pipelining._

trait SyncRoutes { self: RouteStoryService ⇒
  private val changes = (path("_changes") & get) {
    complete(???)
  }

  private val alldocs = (path("_all_docs") & post) {
    complete(???)
  }

  private val bulkdocs = (path("_bulk_docs") & post) {
    complete(???)
  }

  private val revsdiff = (path("_revs_diff") & post) {
    complete(???)
  }

  private val checkpoints = path("_local" / Segment) { id ⇒
    get {
      couchComplete(Get(s"/story2/_local/$id"))
    } ~
    put {
      ???
    }
  }

  private val db = path(PathEnd) {
    couchComplete(Head("/story2"))
  }

  val syncRoutes = db
}
