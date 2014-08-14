package net.routestory.browsing.stories

import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.view.{ Gravity, View, ViewGroup, LayoutInflater }
import android.widget.{ TextView, ProgressBar, FrameLayout }
import com.etsy.android.grid.StaggeredGridView
import macroid.Ui
import net.routestory.data.Story
import net.routestory.ui.{ Styles, RouteStoryFragment }
import net.routestory.viewable.StoryPreviewListable
import macroid.FullDsl._
import macroid.viewable._
import macroid.contrib._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class LocalFragment extends RouteStoryFragment {

  var grid = slot[StaggeredGridView]
  var swiper = slot[SwipeRefreshLayout]
  var empty = slot[TextView]

  def viewStories(stories: List[Story]) = {
    import StoryPreviewListable._
    (grid <~ stories.map(_.preview).listAdapterTweak) ~
      (empty <~ show(stories.isEmpty)) ~
      (swiper <~ Styles.stopRefresh)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[SwipeRefreshLayout](
      l[FrameLayout](
        w[StaggeredGridView] <~ wire(grid) <~ Styles.grid,
        w[TextView] <~ wire(empty) <~ Styles.empty <~
          text("No stories yet!") <~ hide
      )
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

  def fetchStories = app.hybridApi.localStories("story/all").go
}
