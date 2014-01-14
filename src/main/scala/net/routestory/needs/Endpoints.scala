package net.routestory.needs

import java.io.File

import scala.concurrent.{ ExecutionContext, future }

import android.content.Context
import android.util.Log

import com.loopj.android.http.AsyncHttpClient
import org.needs.{ Endpoint, http, rest }
import org.needs.file.{ FileEndpoint, HttpFileEndpoint, LocalFileEndpoint }
import org.needs.http.HttpEndpoint
import org.needs.json.JsonEndpoint

import net.routestory.RouteStoryApp
import org.macroid.AppContext

object HttpClient {
  lazy val client = new AsyncHttpClient
}

case class RouteStoryAppContext(app: RouteStoryApp)

trait EndpointLogging { self: Endpoint ⇒
  override protected def logFetching() {
    Log.d("Needs", s"Downloading $this")
  }
}

/* Local Couch endpoints */

trait LocalEndpoint { self: Endpoint ⇒
  override val priority = Seq(1, 0)
}

trait CouchEndpointBase extends JsonEndpoint with LocalEndpoint with EndpointLogging {
  import net.routestory.lounge.Couch._
  implicit val appCtx: RouteStoryAppContext
  val id: String
  case object NotFound extends Throwable
  protected def fetch(implicit ec: ExecutionContext) = future {
    Option(appCtx.app.couchDb.getExistingDocument(id)).map(_.getProperties.toJsObject).getOrElse(throw NotFound)
  }
}

case class LocalAuthor(id: String)(implicit val appCtx: RouteStoryAppContext)
  extends CouchEndpointBase

case class LocalStory(id: String)(implicit val appCtx: RouteStoryAppContext)
  extends CouchEndpointBase

/* Remote REST API endpoints */

trait RemoteEndpointBase extends http.AndroidJsonClient with EndpointLogging { self: HttpEndpoint with JsonEndpoint ⇒
  val asyncHttpClient = HttpClient.client
}

abstract class SingleResource(val baseUrl: String)
  extends rest.SingleResourceEndpoint with RemoteEndpointBase

case class RemoteAuthor(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/authors")

case class RemoteStory(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/stories")

/* Media endpoints */

abstract class CachedFileBase(url: String, ctx: AppContext) extends FileEndpoint with EndpointLogging {
  def create = new File(s"${ctx.get.getExternalCacheDir.getAbsolutePath}/${url.replace("/", "-")}")
}

case class RemoteMedia(url: String)(implicit ctx: AppContext)
  extends CachedFileBase(url, ctx) with HttpFileEndpoint with http.AndroidFileClient {
  val asyncHttpClient = new AsyncHttpClient
  val baseUrl = "http://routestory.herokuapp.com/api/stories"
}

case class LocalCachedMedia(url: String)(implicit ctx: AppContext)
  extends CachedFileBase(url, ctx) with LocalFileEndpoint with LocalEndpoint

case class LocalTempMedia(url: String)
  extends LocalFileEndpoint with EndpointLogging {
  override val priority = Seq(2)
  def create = new File(url)
}
