package net.routestory

import com.couchbase.lite.android.AndroidContext
import com.couchbase.lite.{ Emitter, Manager, Mapper }

trait Couch { self: RouteStoryApp ⇒
  lazy val couchManager = new Manager(new AndroidContext(self), Manager.DEFAULT_OPTIONS)
  lazy val couchDb = couchManager.getDatabase("story")

  def setViews() = {
    couchDb.getView("story/all").setMap(new Mapper {
      def map(doc: java.util.Map[String, AnyRef], emitter: Emitter) {
        if (doc.get("type") == "story") {
          emitter.emit(doc.get("_id").toString, null)
        }
      }
    }, "1.0")
  }
}
