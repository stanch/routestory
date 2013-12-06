package net.routestory.needs

import com.loopj.android.http.AsyncHttpClient
import org.needs.{ rest, Endpoint }
import android.util.Log
import scala.concurrent.{ Future, ExecutionContext }

object Client {
  lazy val client = new AsyncHttpClient
}

trait EndpointLogging { self: Endpoint ⇒
  override protected def logFetching() {
    Log.d("Needs", s"Downloading $this")
  }
}

trait RestEndpoint extends rest.RestEndpoint with rest.AndroidClient with EndpointLogging {
  val asyncHttpClient = Client.client
}

abstract class SingleResource(val path: String)
  extends RestEndpoint with rest.SingleResourceEndpoint

case class RemoteAuthor(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/authors")

case class RemoteStory(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/stories")

case class LatestStories(num: Int) extends RestEndpoint {
  def fetch(implicit ec: ExecutionContext) =
    client("http://routestory.herokuapp.com/api/stories/latest")
}

case class SearchStories(query: String, limit: Int, bookmark: Option[String]) extends RestEndpoint {
  def fetch(implicit ec: ExecutionContext) = Future.failed(new Exception)
}

case class TaggedStories(tag: String, limit: Int, bookmark: Option[String]) extends RestEndpoint {
  def fetch(implicit ec: ExecutionContext) = Future.failed(new Exception)
}

case class PopularTags() extends RestEndpoint {
  def fetch(implicit ec: ExecutionContext) =
    client("http://routestory.herokuapp.com/api/tags")
}