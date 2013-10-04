package net.routestory.explore

import android.view.{ View, ViewGroup, LayoutInflater }
import android.os.Bundle
import android.widget.{ ScrollView, LinearLayout }
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.parts.RouteStoryFragment
import org.ektorp.ViewQuery
import scala.util.Random
import android.graphics.Point
import scala.collection.JavaConversions._
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import scala.async.Async.{ async, await }

class TagsFragment extends RouteStoryFragment {
  var rows = slot[LinearLayout]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[ScrollView](l[VerticalLinearLayout]() ~> wire(rows) ~> p8dding)
  }

  override def onStart() {
    super.onStart()

    val displaySize = new Point
    getActivity.getWindowManager.getDefaultDisplay.getSize(displaySize)

    async {
      val query = new ViewQuery().designDocId("_design/Story").viewName("tags").group(true)
      val result = await(app.getPlainQueryResults(remote = true, query))
      val tags = Random.shuffle(result.getRows.toList).take(50).map(x ⇒ (x.getKey, x.getValueAsInt))
      val (max, min) = (tags.maxBy(_._2)._2, tags.minBy(_._2)._2)
      def n(x: Int) = if (max == min) 1 else (x - min).toDouble / (max - min)
      val norm = tags.map { case (t, q) ⇒ (t, Some(n(q))) }
      Ui(ResultRow.fillTags(rows, displaySize.x - 30, norm.toArray, getActivity))
    }
  }
}
