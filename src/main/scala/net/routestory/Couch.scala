package net.routestory

import com.couchbase.lite.android.AndroidContext
import com.couchbase.lite.{ Emitter, Manager, Mapper }
import scala.collection.JavaConverters._

trait Couch { self: RouteStoryApp ⇒
  lazy val couchManager = new Manager(new AndroidContext(self), Manager.DEFAULT_OPTIONS)
  lazy val couchDb = couchManager.getDatabase("story")

  def setCouchViews() = {
    couchDb.getView("story/all").setMap(new Mapper {
      def map(doc: java.util.Map[String, AnyRef], emitter: Emitter) {
        if (doc.get("type") == "story") {
          val start = doc.get("chapters").asInstanceOf[java.util.List[AnyRef]]
            .get(0).asInstanceOf[java.util.Map[String, AnyRef]]
            .get("start")
          val meta = doc.get("meta").asInstanceOf[java.util.Map[String, AnyRef]]
          val title = meta.get("title")
          val tags = meta.get("tags")
          val authorId = doc.get("authorId")
          emitter.emit(start, Map("title" → title, "tags" → tags, "authorId" → authorId).asJava)
        }
      }
    }, "6")
  }
}
