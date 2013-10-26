package net.routestory.bag

import spray.client.pipelining._
import spray.routing.{PathMatcher1, PathMatcher}
import shapeless._
import spray.http.Uri.Path
import spray.routing.PathMatcher.{Unmatched, Matched, Matching}
import scala.async.Async.{async, await}
import play.api.libs.json._

object Repeat {
  implicit class RepeatingPathMatcher[L <: HList](pm: PathMatcher[L]) {
    def repeat = new PathMatcher1[List[L]] {
      def apply(path: Path): Matching[List[L] :: HNil] = pm(path) match {
        case Matched(rest, extractions) ⇒
          val Matched(p, l :: HNil) = this(rest)
          Matched(p, (extractions :: l) :: HNil)
        case Unmatched ⇒
          Matched(path, Nil :: HNil)
      }
    }
  }
  implicit class RepeatingPathMatcher1[T](pm: PathMatcher1[T]) {
    def repeat = new PathMatcher1[List[T]] {
      def apply(path: Path): Matching[List[T] :: HNil] = pm(path) match {
        case Matched(rest, extractions) ⇒
          val Matched(p, l :: HNil) = this(rest)
          Matched(p, (extractions.head :: l) :: HNil)
        case Unmatched ⇒
          Matched(path, Nil :: HNil)
      }
    }
  }
}

trait AuthorRoutes { self: RouteStoryService ⇒
  import Repeat._

  private val authorId = Shortuuid.matcher("author")

  private val authors = (path(authorId ~ ("," ~ authorId).repeat) & get) { (id, ids) ⇒ ctx ⇒
    if (ids.isEmpty) {
      couchComplete(Get(Couch.docUri(id)))(ctx)
    } else async {
      val authors = await(couchJsonPipeline(Couch.docsReq(id :: ids)))
      complete(Json.toJson(authors \\ "doc"))(ctx)
    }
  }

  val authorRoutes = pathPrefix("authors") { authors }
}
