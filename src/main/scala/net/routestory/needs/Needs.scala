package net.routestory.needs

import org.needs._
import net.routestory.model2._
import scala.concurrent.{ Future, ExecutionContext }
import org.needs.http.HttpEndpoint
import JsonFormats._

case class NeedAuthor(id: String) extends Need[Author] with rest.Probing[Author] {
  use { RemoteAuthor(id) }
  from { singleResource[RemoteAuthor] }
}

case class NeedStory(id: String) extends Need[Story] with rest.Probing[Story] {
  use { RemoteStory(id) }
  from { singleResource[RemoteStory] }
}

case class NeedLatest(num: Int)
  extends json.SelfFulfillingNeed[Latest] with HttpEndpoint with RemoteEndpointBase {
  def fetch(implicit ec: ExecutionContext) =
    client("http://routestory.herokuapp.com/api/stories/latest")
}

case class NeedSearch(query: String, limit: Int = 4, bookmark: Option[String] = None)
  extends json.SelfFulfillingNeed[Searched] with HttpEndpoint with RemoteEndpointBase {
  def fetch(implicit ec: ExecutionContext) = Future.failed(new Exception)
}

case class NeedTagged(tag: String, limit: Int = 4, bookmark: Option[String] = None)
  extends json.SelfFulfillingNeed[Searched] with HttpEndpoint with RemoteEndpointBase {
  def fetch(implicit ec: ExecutionContext) = Future.failed(new Exception)
}

case class NeedTags() extends json.SelfFulfillingNeed[List[Tag]] with HttpEndpoint with RemoteEndpointBase {
  def fetch(implicit ec: ExecutionContext) =
    client("http://routestory.herokuapp.com/api/tags")
}
