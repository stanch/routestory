package net.routestory.browsing.stories

import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.view.{ LayoutInflater, View, ViewGroup }
import com.etsy.android.grid.StaggeredGridView
import macroid.FullDsl._
import macroid.Ui
import net.routestory.data.StoryPreview
import net.routestory.ui.{ RouteStoryFragment, Styles }
import net.routestory.viewable.StoryPreviewListable
import macroid.viewable._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class LatestFragment extends RouteStoryFragment {

  var grid = slot[StaggeredGridView]
  var swiper = slot[SwipeRefreshLayout]

  lazy val number = Option(getArguments).map(_.getInt("number")).getOrElse(3)

  def viewStories(stories: List[StoryPreview]) = {
    import StoryPreviewListable._
    stories.listAdapterTweak
    (grid <~ stories.listAdapterTweak) ~ (swiper <~ Styles.stopRefresh)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[SwipeRefreshLayout](
      w[StaggeredGridView] <~ wire(grid) <~ Styles.grid
    ) <~ Styles.swiper <~ On.refresh[SwipeRefreshLayout](refresh) <~ wire(swiper)
  }

  override def onStart() = {
    super.onStart()
    runUi(
      swiper <~ Styles.startRefresh,
      refresh
    )
  }

  def refresh = Ui {
    fetchStories.map(viewStories(_).run).recover {
      case NonFatal(t) ⇒ (swiper <~ Styles.stopRefresh).run
    }
  }

  def fetchStories = app.webApi.latest(number).go.map(_.stories)
}
