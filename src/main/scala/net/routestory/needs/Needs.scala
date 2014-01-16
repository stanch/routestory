package net.routestory.needs

import java.io.File
import scala.concurrent.ExecutionContext
import org.needs._

import net.routestory.model._
import org.macroid.AppContext

trait Needs { self: Shared with Endpoints with LoadingFormats ⇒
  case class NeedAuthor(id: String) extends Need[Author] with rest.Probing[Author] {
    use(LocalAuthor(id), RemoteAuthor(id))
    from {
      singleResource[RemoteAuthor]
    }
    from {
      case e @ LocalAuthor(`id`) ⇒ e.probeAs[Author]
    }
    prioritize {
      case LocalAuthor(_) ⇒ Seq(1)
    }
  }

  case class NeedStory(id: String) extends Need[Story] with rest.Probing[Story] {
    use(LocalStory(id), RemoteStory(id))
    from {
      singleResource[RemoteStory]
    }
    from {
      case e @ LocalStory(`id`) ⇒ e.probeAs[Story]
    }
    prioritize {
      case LocalStory(_) ⇒ Seq(1)
    }
  }

  case class NeedLatest(num: Int)
    extends json.SelfFulfillingNeed[Latest] with RemoteEndpoint {
    def fetch(implicit ec: ExecutionContext) =
      client.getJson("http://routestory.herokuapp.com/api/stories/latest", Map("limit" → num.toString))
  }

  case class NeedSearch(query: String, limit: Int = 4, bookmark: Option[String] = None)
    extends json.SelfFulfillingNeed[Searched] with RemoteEndpoint {
    def fetch(implicit ec: ExecutionContext) =
      client.getJson(s"http://routestory.herokuapp.com/api/stories/search/$query",
        Map("limit" → limit.toString) ++ bookmark.map(b ⇒ Map("bookmark" → b)).getOrElse(Map.empty))
  }

  case class NeedTagged(tag: String, limit: Int = 4, bookmark: Option[String] = None)
    extends json.SelfFulfillingNeed[Searched] with RemoteEndpoint {
    def fetch(implicit ec: ExecutionContext) =
      client.getJson(s"http://routestory.herokuapp.com/api/tags/$tag/stories",
        Map("limit" → limit.toString) ++ bookmark.map(b ⇒ Map("bookmark" → b)).getOrElse(Map.empty))
  }

  case class NeedTags() extends json.SelfFulfillingNeed[List[Tag]] with RemoteEndpoint {
    def fetch(implicit ec: ExecutionContext) =
      client.getJson("http://routestory.herokuapp.com/api/tags")
  }

  case class NeedMedia(url: String) extends Need[File] {
    use { RemoteMedia(url) }
    use { LocalCachedMedia(url) }
    use { LocalTempMedia(url) }
    from {
      case e @ LocalTempMedia(`url`) ⇒ e.probe
      case e @ LocalCachedMedia(`url`) ⇒ e.probe
      case e @ RemoteMedia(`url`) ⇒ e.probe
    }
    prioritize {
      case LocalCachedMedia(_) ⇒ Seq(2)
      case LocalTempMedia(_) ⇒ Seq(1)
    }
  }
}
