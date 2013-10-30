package net.routestory.bag

import spray.client.pipelining._
import spray.routing.PathMatcher.Lift
import scala.async.Async.{async, await}
import play.api.libs.json._

trait AuthorRoutes { self: RouteStoryService ⇒
  private val authorId = Shortuuid.matcher("author")

  private val authors = (path(authorId ~ ("," ~ authorId).repeat()) & get) { (id, ids) ⇒ ctx ⇒
    if (ids.isEmpty) {
      couchComplete(Get(Couch.docUri(id)))(ctx)
    } else async {
      val authors = await(couchJsonPipeline(Couch.docsReq(id :: ids)))
      complete(Json.toJson(authors \\ "doc"))(ctx)
    }
  }

  val authorRoutes = pathPrefix("authors") { authors }
}
