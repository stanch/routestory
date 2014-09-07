package net.routestory.browsing.stories

import android.content.Intent
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.view.{ Gravity, View, ViewGroup, LayoutInflater }
import android.widget._
import com.etsy.android.grid.StaggeredGridView
import macroid.Ui
import macroid.contrib.ImageTweaks
import macroid.contrib.Layouts.{ VerticalLinearLayout, HorizontalLinearLayout }
import net.routestory.R
import net.routestory.data.{ StoryPreview, Story }
import net.routestory.recording.RecordActivity
import net.routestory.ui.{ Styles, Tweaks, RouteStoryFragment }
import net.routestory.viewable.StoryPreviewListable
import macroid.FullDsl._
import macroid.viewable._
import android.view.ViewGroup.LayoutParams._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class LocalFragment extends RouteStoryFragment {

  var grid = slot[StaggeredGridView]
  var swiper = slot[SwipeRefreshLayout]
  var empty = slot[LinearLayout]

  def viewStories(stories: List[StoryPreview]) = {
    import StoryPreviewListable._
    (grid <~ stories.listAdapterTweak) ~
      (empty <~ show(stories.isEmpty)) ~
      (swiper <~ Tweaks.stopRefresh)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[SwipeRefreshLayout](
      l[FrameLayout](
        w[StaggeredGridView] <~ wire(grid) <~ Styles.grid,
        l[VerticalLinearLayout](
          w[TextView] <~ text("No stories yet!") <~ Styles.empty,
          l[HorizontalLinearLayout](
            w[TextView] <~ text("Click “") <~ Styles.empty,
            w[ImageView] <~ ImageTweaks.res(R.drawable.ic_action_new),
            w[TextView] <~ text("” to create a story.") <~ Styles.empty
          )
        ) <~ wire(empty) <~ hide <~
          lp[FrameLayout](WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER) <~
          On.click(Ui(startActivity(new Intent(getActivity, classOf[RecordActivity]))))
      )
    ) <~ Styles.swiper <~ On.refresh[SwipeRefreshLayout](refresh) <~ wire(swiper)
  }

  override def onStart() = {
    super.onStart()
    runUi(
      swiper <~ Tweaks.startRefresh,
      refresh
    )
  }

  def refresh = Ui {
    fetchStories.map(viewStories(_).run).recover {
      case NonFatal(t) ⇒ (swiper <~ Tweaks.stopRefresh).run
    }
  }

  def fetchStories = app.hybridApi.localStories("story/all").go
}
