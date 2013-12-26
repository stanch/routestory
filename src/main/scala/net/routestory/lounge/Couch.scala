package net.routestory.lounge

import android.content.Context

import com.couchbase.lite.{ Emitter, Manager, Mapper }
import com.fasterxml.jackson.databind.ObjectMapper
import play.api.libs.json._

import net.routestory.model.{ Author, Story }
import net.routestory.needs.JsonFormats
import JsonFormats._
import net.routestory.RouteStoryApp

object Couch {
  implicit class RichJsObject(js: JsObject) {
    def toJavaMap = (new ObjectMapper).readValue(js.toString(), classOf[java.util.Map[String, Object]])
  }

  implicit class RichJavaMap(map: java.util.Map[String, Object]) {
    def toJsObject = Json.parse((new ObjectMapper).writeValueAsString(map)).as[JsObject]
  }
}

trait Couch { self: RouteStoryApp ⇒
  import Couch._

  lazy val couchManager = new Manager(getFilesDir, Manager.DEFAULT_OPTIONS)
  lazy val couchDb = {
    val db = couchManager.getDatabase("story")
    db.open(); db
  }

  def setViews(author: Option[Author]) {
    couchDb.getView("story/my").setMap(new Mapper {
      def map(doc: java.util.Map[String, AnyRef], emitter: Emitter) {
        if (doc.get("type") == "story") {
          emitter.emit(doc.get("_id").toString, null)
        }
      }
    }, "1.0")
  }

  def createStory(story: Story) = {
    val json = Json.toJson(story).as[JsObject].toJavaMap
    val doc = couchDb.getDocument(story.id)
    doc.putProperties(json)
    //    val rev = doc.createRevision()
    //    rev.setUserProperties(json)
    //    rev.save()
  }
}
