package net.routestory.couch

import com.couchbase.lite.Database
import org.codehaus.jackson.map.ObjectMapper
import play.api.libs.json.{JsArray, Json, JsObject}
import resolvable.EndpointLogger
import resolvable.json.JsonEndpoint
import scala.collection.JavaConversions._

import scala.concurrent.{Future, ExecutionContext}

trait Endpoints extends JsonHelpers {
  def couch: Database

  abstract class CouchDocEndpoint(id: String) extends JsonEndpoint {
    val logger = EndpointLogger.none
    case object NotFound extends Throwable
    protected def fetch(implicit ec: ExecutionContext) = Future {
      Option(couch.getExistingDocument(id)).map(_.getProperties.toJsObject).getOrElse(throw NotFound)
    }
  }

  case class LocalAuthor(id: String) extends CouchDocEndpoint(id)
  case class LocalStory(id: String) extends CouchDocEndpoint(id)

  case class LocalView(name: String) extends JsonEndpoint {
    val logger = EndpointLogger.none
    protected def fetch(implicit ec: ExecutionContext) = Future {
      JsArray {
        val q = couch.getView(name).createQuery()
        q.setDescending(true)
        q.run().map(_.getDocument.getProperties.toJsObject).toSeq
      }
    }
  }
}
