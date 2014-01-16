package net.routestory.explore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

import android.graphics.Point
import android.os.Bundle
import android.view.{ LayoutInflater, View, ViewGroup }
import android.widget.{ LinearLayout, ScrollView }

import org.macroid.FullDsl._
import org.macroid.contrib.Layouts.VerticalLinearLayout

import net.routestory.ui.RouteStoryFragment
import net.routestory.ui.Styles._

class TagsFragment extends RouteStoryFragment {
  var rows = slot[LinearLayout]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[ScrollView](l[VerticalLinearLayout]() ~> wire(rows) ~> p8dding)
  }

  override def onStart() {
    super.onStart()

    val displaySize = new Point
    getActivity.getWindowManager.getDefaultDisplay.getSize(displaySize)

    app.api.NeedTags().go foreachUi { tags ⇒
      val shuffled = Random.shuffle(tags).take(50)
      val (max, min) = (shuffled.maxBy(_.count).count, shuffled.minBy(_.count).count)
      def n(x: Int) = if (max == min) 1 else (x - min).toDouble / (max - min)
      val norm = shuffled.map(t ⇒ (t.tag, Some(n(t.count))))
      PreviewRow.fillTags(rows, displaySize.x - (20 dp), norm)
    }
  }
}
