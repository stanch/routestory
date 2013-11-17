package net.routestory.explore

import android.view.{ View, ViewGroup, LayoutInflater }
import android.os.Bundle
import android.widget.{ ScrollView, LinearLayout }
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.parts.RouteStoryFragment
import scala.util.Random
import android.graphics.Point
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import net.routestory.lounge2.Lounge
import scala.ref.WeakReference

class TagsFragment extends RouteStoryFragment {
  var rows = slot[LinearLayout]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[ScrollView](l[VerticalLinearLayout]() ~> wire(rows) ~> p8dding)
  }

  override def onStart() {
    super.onStart()

    val displaySize = new Point
    getActivity.getWindowManager.getDefaultDisplay.getSize(displaySize)

    Lounge.popularTags foreachUi { tags ⇒
      val shuffled = Random.shuffle(tags).take(50).map(x ⇒ (x.key, x.data))
      val (max, min) = (shuffled.maxBy(_._2)._2, shuffled.minBy(_._2)._2)
      def n(x: Int) = if (max == min) 1 else (x - min).toDouble / (max - min)
      val norm = shuffled.map { case (t, q) ⇒ (t, Some(n(q))) }
      PreviewRow.fillTags(rows, displaySize.x - (20 dp), norm, WeakReference(getActivity))
    }
  }
}
