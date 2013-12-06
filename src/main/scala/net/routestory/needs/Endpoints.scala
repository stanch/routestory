package net.routestory.needs

import com.loopj.android.http.AsyncHttpClient
import org.needs.{ rest, http, Endpoint }
import android.util.Log
import org.needs.http.HttpEndpoint
import org.needs.json.JsonEndpoint

object Client {
  lazy val client = new AsyncHttpClient
}

trait EndpointLogging { self: Endpoint ⇒
  override protected def logFetching() {
    Log.d("Needs", s"Downloading $this")
  }
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
