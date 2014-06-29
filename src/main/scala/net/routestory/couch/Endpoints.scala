package net.routestory.couch

import com.couchbase.lite.Database
import org.codehaus.jackson.map.ObjectMapper
import play.api.libs.json.{Json, JsObject}
import resolvable.EndpointLogger
import resolvable.json.JsonEndpoint

import scala.concurrent.{Future, ExecutionContext}

trait Endpoints {
  def couch: Database

  implicit class RichJsObject(js: JsObject) {
    def toJavaMap = (new ObjectMapper).readValue(js.toString(), classOf[java.util.Map[String, Object]])
  }

  implicit class RichJavaMap(map: java.util.Map[String, Object]) {
    def toJsObject = Json.parse((new ObjectMapper).writeValueAsString(map)).asInstanceOf[JsObject]
  }

  abstract class CouchEndpoint(id: String) extends JsonEndpoint {
    val logger = EndpointLogger.none
    case object NotFound extends Throwable
    protected def fetch(implicit ec: ExecutionContext) = Future {
      Option(couch.getExistingDocument(id)).map(_.getProperties.toJsObject).getOrElse(throw NotFound)
    }
  }

  case class LocalAuthor(id: String) extends CouchEndpoint(id)
  case class LocalStory(id: String) extends CouchEndpoint(id)
}
