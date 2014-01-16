package net.routestory.needs

import java.io.File

import scala.concurrent.{ Future, ExecutionContext, future }

import android.content.{ SharedPreferences, Context }
import android.util.Log

import com.loopj.android.http.AsyncHttpClient
import org.needs.{ EndpointLogger, Endpoint, http, rest }
import org.needs.file.{ FileEndpoint, HttpFileEndpoint, LocalFileEndpoint }
import org.needs.http.{ AndroidClient, HttpClient, HttpEndpoint }
import org.needs.json.{ HttpJsonEndpoint, JsonEndpoint }

import net.routestory.RouteStoryApp
import org.macroid.AppContext

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

  case class LocalAuthor(id: String)
    extends CouchEndpoint(id)

  case class LocalStory(id: String)
    extends CouchEndpoint(id)

  /* Remote REST API endpoints */

  trait RemoteEndpoint extends HttpJsonEndpoint {
    val client = httpClient
    val logger = endpointLogger
  }

  abstract class SingleResource(val baseUrl: String)
    extends rest.SingleResourceEndpoint with RemoteEndpoint

  case class RemoteAuthor(id: String)
    extends SingleResource("http://routestory.herokuapp.com/api/authors")

  case class RemoteStory(id: String)
    extends SingleResource("http://routestory.herokuapp.com/api/stories")

  /* Media endpoints */

  abstract class CachedFile(url: String) extends FileEndpoint {
    val logger = endpointLogger
    def create = new File(s"${appCtx.getExternalCacheDir.getAbsolutePath}/${url.replace("/", "-")}")
  }

  case class RemoteMedia(url: String) extends CachedFile(url) with HttpFileEndpoint {
    val client = AndroidClient(new AsyncHttpClient)
    val baseUrl = "http://routestory.herokuapp.com/api/stories"
  }

  case class LocalCachedMedia(url: String)
    extends CachedFile(url) with LocalFileEndpoint

  case class LocalTempMedia(url: String) extends LocalFileEndpoint {
    val logger = endpointLogger
    def create = new File(url)
  }
}
