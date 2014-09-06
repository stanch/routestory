package net.routestory.browsing.stories

import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.view.{ LayoutInflater, View, ViewGroup }
import android.widget.ScrollView
import macroid.FullDsl._
import macroid.LogTag
import macroid.Ui
import net.routestory.ui.Styles._
import net.routestory.ui.{ Tweaks, RouteStoryFragment, Styles }
import net.routestory.viewable.StoryPreviewListable
import org.apmem.tools.layouts.FlowLayout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.util.control.NonFatal

class TagsFragment extends RouteStoryFragment {
  var tags = slot[FlowLayout]
  var swiper = slot[SwipeRefreshLayout]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[SwipeRefreshLayout](
      l[ScrollView](
        l[FlowLayout]() <~ p8dding <~ wire(tags)
      )
    ) <~ Styles.swiper <~ On.refresh[SwipeRefreshLayout](refresh) <~ wire(swiper)
  }

  override def onStart() = {
    super.onStart()
    refresh.run.recover {
      case t ⇒ t.printStackTrace()
    }
  }

  implicit val logTag = LogTag("TagsFrag")

  def refresh = Ui {
    app.webApi.tags.go map { t ⇒
      val shuffled = Random.shuffle(t).take(50)
      val (max, min) = (shuffled.maxBy(_.count).count, shuffled.minBy(_.count).count)
      def n(x: Int) = if (max == min) 1 else (x - min).toDouble / (max - min)
      val views = shuffled.map(t ⇒ StoryPreviewListable.tag(t.tag, Some(n(t.count))))
      runUi(
        tags <~ addViews(views, removeOld = true),
        swiper <~ Tweaks.stopRefresh
      )
    } recover {
      case NonFatal(t) ⇒ t.printStackTrace()
    }
  }
}
