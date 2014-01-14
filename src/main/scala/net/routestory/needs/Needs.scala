package net.routestory.needs

import java.io.File

import scala.concurrent.ExecutionContext

import android.content.Context

import org.needs._
import org.needs.http.HttpEndpoint

import net.routestory.model._
import net.routestory.needs.JsonFormats._
import org.macroid.AppContext

case class NeedAuthor(id: String)(implicit appCtx: RouteStoryAppContext) extends Need[Author] with rest.Probing[Author] {
  use(LocalAuthor(id), RemoteAuthor(id))
  from { singleResource[RemoteAuthor] }
  from {
    case e @ LocalAuthor(`id`) ⇒ e.probeAs[Author]
  }
}

case class NeedStory(id: String)(implicit appCtx: RouteStoryAppContext) extends Need[Story] with rest.Probing[Story] {
  use(LocalStory(id), RemoteStory(id))
  from { singleResource[RemoteStory] }
  from {
    case e @ LocalStory(`id`) ⇒ e.probeAs[Story]
  }
}

case class NeedLatest(num: Int)(implicit appCtx: RouteStoryAppContext)
  extends json.SelfFulfillingNeed[Latest] with HttpEndpoint with RemoteEndpointBase {
  def fetch(implicit ec: ExecutionContext) =
    client("http://routestory.herokuapp.com/api/stories/latest", Map("limit" → num.toString))
}

case class NeedSearch(query: String, limit: Int = 4, bookmark: Option[String] = None)(implicit appCtx: RouteStoryAppContext)
  extends json.SelfFulfillingNeed[Searched] with HttpEndpoint with RemoteEndpointBase {
  def fetch(implicit ec: ExecutionContext) =
    client(s"http://routestory.herokuapp.com/api/stories/search/$query",
      Map("limit" → limit.toString) ++ bookmark.map(b ⇒ Map("bookmark" → b)).getOrElse(Map.empty))
}

case class NeedTagged(tag: String, limit: Int = 4, bookmark: Option[String] = None)(implicit appCtx: RouteStoryAppContext)
  extends json.SelfFulfillingNeed[Searched] with HttpEndpoint with RemoteEndpointBase {
  def fetch(implicit ec: ExecutionContext) =
    client(s"http://routestory.herokuapp.com/api/tags/$tag/stories",
      Map("limit" → limit.toString) ++ bookmark.map(b ⇒ Map("bookmark" → b)).getOrElse(Map.empty))
}

case class NeedTags() extends json.SelfFulfillingNeed[List[Tag]] with HttpEndpoint with RemoteEndpointBase {
  def fetch(implicit ec: ExecutionContext) =
    client("http://routestory.herokuapp.com/api/tags")
}

case class NeedMedia(url: String)(implicit ctx: AppContext) extends Need[File] {
  use { RemoteMedia(url) }
  use { LocalCachedMedia(url) }
  use { LocalTempMedia(url) }
  from {
    case e @ LocalTempMedia(`url`) ⇒ e.probe
    case e @ LocalCachedMedia(`url`) ⇒ e.probe
    case e @ RemoteMedia(`url`) ⇒ e.probe
  }
}
