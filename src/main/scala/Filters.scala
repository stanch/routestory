import spray.http._
import spray.httpx.marshalling._
import spray.httpx.unmarshalling._
import spray.routing.Directive0
import spray.json._
import DefaultJsonProtocol._
import spray.httpx.SprayJsonSupport._
import org.mutate.Mutate._
import spray.routing.directives.BasicDirectives

object Filters extends BasicDirectives {
  implicit val objectFormat = new RootJsonFormat[JsObject] {
    def write(value: JsObject) = value
    def read(value: JsValue) = value.asJsObject
  }

  implicit class RichHttpMessage[A <: HttpMessage](val m: A) {
    def mapEntityJson(f: JsObject ⇒ JsValue): m.Self = m.mapEntity { entity ⇒
      entity.as[JsObject].right.flatMap(j ⇒ marshal(f(j).asJsObject)) match {
        case Right(newEntity) ⇒ newEntity
        case _ ⇒ entity
      }
    }
  }

  implicit class RichJsObject(j: JsObject) {
    def filterKeys(f: String ⇒ Boolean): JsObject = j.copy(fields = j.fields.filterKeys(f))
  }

  def filterParameters(f: String ⇒ Boolean): Directive0 = mapRequestContext(mutate(_) { $ ⇒
    $.request.uri.query ~= (_.filter(v ⇒ f(v._2)))
  })
}