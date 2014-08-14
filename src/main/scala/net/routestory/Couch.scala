package net.routestory

import android.util.Log
import com.couchbase.lite.android.AndroidContext
import com.couchbase.lite.{ Emitter, Manager, Mapper }

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
          Log.w("Couch", s"start is $start")
          emitter.emit(start, null)
        }
      }
    }, "5")
  }
}
