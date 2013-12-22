package net.routestory.needs

import com.loopj.android.http.AsyncHttpClient
import org.needs.{ rest, http, Endpoint }
import android.util.Log
import org.needs.http.HttpEndpoint
import org.needs.json.JsonEndpoint
import org.needs.file.{ LocalFileEndpoint, FileEndpoint }
import java.io.File
import scala.concurrent.{ Future, ExecutionContext }
import android.content.Context

object Client {
  lazy val client = new AsyncHttpClient
}

trait EndpointLogging { self: Endpoint ⇒
  override protected def logFetching() {
    Log.d("Needs", s"Downloading $this")
  }
}

trait Local { self: Endpoint ⇒
  override val priority = Seq(1, 0)
}

trait RemoteEndpointBase extends http.AndroidJsonClient with EndpointLogging { self: HttpEndpoint with JsonEndpoint ⇒
  val asyncHttpClient = Client.client
}

abstract class SingleResource(val path: String)
  extends rest.SingleResourceEndpoint with RemoteEndpointBase

case class RemoteAuthor(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/authors")

case class RemoteStory(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/stories")

abstract class CachedFileBase(url: String, ctx: Context) extends FileEndpoint with EndpointLogging {
  def create = new File(s"${ctx.getExternalCacheDir.getAbsolutePath}/${url.replace("/", "-")}")
}

case class RemoteMedia(url: String)(implicit ctx: Context)
  extends CachedFileBase(url, ctx) with http.HttpEndpoint with http.AndroidFileClient {
  // TODO: WTF? loopj is a piece of crap? can’t we reuse the global one?
  val asyncHttpClient = new AsyncHttpClient //Client.client
  protected def fetch(implicit ec: ExecutionContext) =
    client(s"http://routestory.herokuapp.com/api/stories/$url")
}

case class LocalCachedMedia(url: String)(implicit ctx: Context)
  extends CachedFileBase(url, ctx) with LocalFileEndpoint with Local

case class LocalTempMedia(url: String)
  extends FileEndpoint with EndpointLogging with LocalFileEndpoint {
  override val priority = Seq(2)
  def create = new File(url)
}