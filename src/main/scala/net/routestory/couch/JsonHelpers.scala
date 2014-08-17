package net.routestory.couch

import org.codehaus.jackson.map.ObjectMapper
import play.api.libs.json.{Json, JsObject}

trait JsonHelpers {
  implicit class RichJsObject(js: JsObject) {
    def toJavaMap = (new ObjectMapper).readValue(js.toString(), classOf[java.util.Map[String, Object]])
  }

  implicit class RichJavaMap(map: java.util.Map[String, Object]) {
    def toJsObject = Json.parse((new ObjectMapper).writeValueAsString(map)).asInstanceOf[JsObject]
  }
}
