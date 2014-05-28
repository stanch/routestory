package net.routestory.needs

import java.io.File

import scala.concurrent.{ Future, ExecutionContext, future }

import android.util.Log

import com.loopj.android.http.AsyncHttpClient
import resolvable.file.{ FileEndpoint, HttpFileEndpoint, LocalFileEndpoint }
import resolvable.http.AndroidClient
import resolvable.json.{ HttpJsonEndpoint, JsonEndpoint }

import java.util.concurrent.Executors

trait Endpoints { self: Shared ⇒

  /* Local Couch endpoints */

  abstract class CouchEndpoint(id: String) extends JsonEndpoint {
    import net.routestory.lounge.Couch._
    val logger = endpointLogger
    case object NotFound extends Throwable
    protected def fetch(implicit ec: ExecutionContext) = future {
      Option(couchDb.getExistingDocument(id)).map(_.getProperties.toJsObject).getOrElse(throw NotFound)
    }
  }

  case class LocalAuthor(id: String) extends CouchEndpoint(id)
  case class LocalStory(id: String) extends CouchEndpoint(id)

  /* Remote REST API endpoints */

  abstract class RouteStoryEndpoint(path: String, query: (String, String)*) extends HttpJsonEndpoint {
    val client = httpClient
    val logger = endpointLogger
    override def fetch(implicit ec: ExecutionContext) =
      client.getJson(s"http://routestory.herokuapp.com/api/$path", query = Map(query: _*))
  }

  case class RemoteAuthor(id: String) extends RouteStoryEndpoint(s"authors/$id")
  case class RemoteStory(id: String) extends RouteStoryEndpoint(s"stories/$id")
  case class RemoteLatest(num: Int) extends RouteStoryEndpoint("stories/latest", "limit" → num.toString)
  case class RemoteTags() extends RouteStoryEndpoint("tags")

  case class RemoteSearch(query: String, limit: Int = 4, bookmark: Option[String] = None)
    extends RouteStoryEndpoint(s"stories/search/$query", Seq("limit" → limit.toString) ++ bookmark.map("bookmark" → _).toSeq: _*)

  case class RemoteTagged(tag: String, limit: Int = 4, bookmark: Option[String] = None)
    extends RouteStoryEndpoint(s"tags/$tag/stories", Seq("limit" → limit.toString) ++ bookmark.map("bookmark" → _).toSeq: _*)

  /* Media endpoints */

  lazy val externalMediaEc = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

  abstract class CachedFile(url: String) extends FileEndpoint {
    val logger = endpointLogger
    def create = new File(s"${appCtx.getExternalCacheDir.getAbsolutePath}/${url.replace("/", "-").replace(":", "-")}")
  }

  case class RemoteMedia(url: String) extends CachedFile(url) with HttpFileEndpoint {
    val client = AndroidClient(new AsyncHttpClient)
    override protected def fetch(implicit ec: ExecutionContext) =
      client.getFile(s"http://routestory.herokuapp.com/api/stories/$url")(externalMediaEc)
  }

  case class RemoteExternalMedia(url: String) extends CachedFile(url) with HttpFileEndpoint {
    val client = AndroidClient(new AsyncHttpClient)
    override protected def fetch(implicit ec: ExecutionContext) = client.getFile(url)(externalMediaEc)
  }

  case class LocalCachedMedia(url: String) extends CachedFile(url) with LocalFileEndpoint

  case class LocalTempMedia(url: String) extends LocalFileEndpoint {
    val logger = endpointLogger
    def create = new File(url)
  }
}
