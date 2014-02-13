package net.routestory.lounge

import com.couchbase.lite.{ Attachment, Emitter, Manager, Mapper }
import com.fasterxml.jackson.databind.ObjectMapper
import play.api.libs.json._

import net.routestory.model.{ Author, Story }
import net.routestory.needs.SavingFormats
import net.routestory.RouteStoryApp
import java.io.File
import net.routestory.util.Shortuuid
import play.api.data.mapping.To

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
  import SavingFormats._

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
    // relocate local files to attachments
    var files = Map.empty[String, String]
    val updated = story.copy(chapters = story.chapters.map { chapter ⇒
      chapter.copy(media = chapter.media.map {
        case m: Story.HeavyMedia if new File(m.url).isAbsolute ⇒
          val url = story.id + "/" + Shortuuid.make("media")
          files += url → m.url
          m match {
            case x: Story.Sound ⇒ x.copy(url = url)
            case x: Story.VoiceNote ⇒ x.copy(url = url)
            case x: Story.Photo ⇒ x.copy(url = url)
          }
        case x ⇒ x
      })
    })
    val json = To[Story, JsObject](updated).toJavaMap
    val doc = couchDb.getDocument(story.id)
    val rev = doc.putProperties(json).createRevision()
    //files.foreach { case (u, f) ⇒ rev.addAttachment(new Attachment()) }
    rev.save()
  }
}
