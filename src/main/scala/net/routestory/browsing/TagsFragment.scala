package net.routestory.browsing

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

import android.graphics.Point
import android.os.Bundle
import android.view.{ LayoutInflater, View, ViewGroup }
import android.widget.{ LinearLayout, ScrollView }

import macroid.FullDsl._

import net.routestory.ui.RouteStoryFragment
import net.routestory.ui.Styles._
import net.routestory.display.StoryPreviewViewable
import org.apmem.tools.layouts.FlowLayout
import android.support.v4.widget.SwipeRefreshLayout
import macroid.util.Ui
import macroid.{ LogTag, Tweak }
import android.view.ViewGroup.LayoutParams._
import scala.util.control.NonFatal

class TagsFragment extends RouteStoryFragment {
  var tags = slot[FlowLayout]
  var swiper = slot[SwipeRefreshLayout]

  val stopRefresh = Tweak[SwipeRefreshLayout](_.setRefreshing(false))

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[SwipeRefreshLayout](
      l[ScrollView](
        l[FlowLayout]() <~ p8dding <~ wire(tags)
      )
    ) <~ On.refresh[SwipeRefreshLayout](refresh) <~ wire(swiper)
  }

  override def onStart() = {
    super.onStart()
    refresh.run.recover {
      case t ⇒ t.printStackTrace()
    }
  }

  implicit val logTag = LogTag("TagsFrag")

  def refresh = Ui {
    val displaySize = new Point
    getActivity.getWindowManager.getDefaultDisplay.getSize(displaySize)

    app.api.tags.go map { t ⇒
      val shuffled = Random.shuffle(t).take(50)
      val (max, min) = (shuffled.maxBy(_.count).count, shuffled.minBy(_.count).count)
      def n(x: Int) = if (max == min) 1 else (x - min).toDouble / (max - min)
      val views = shuffled.map(t ⇒ StoryPreviewViewable.tag(t.tag, Some(n(t.count))))
      runUi(
        tags <~ addViews(views, removeOld = true),
        swiper <~ stopRefresh
      )
    } recover {
      case NonFatal(t) ⇒ t.printStackTrace()
    }
  }
}
