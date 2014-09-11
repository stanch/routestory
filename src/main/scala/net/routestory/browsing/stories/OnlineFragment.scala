package net.routestory.browsing.stories

import android.os.Bundle
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import macroid.FullDsl._
import macroid.Ui
import net.routestory.data.StoryPreview
import net.routestory.ui.{ RouteStoryFragment, SwipingStaggeredFragment, Tweaks }
import net.routestory.viewable.StoryPreviewListable
import macroid.viewable._
import net.routestory.util.ResolvableLoader

import scala.concurrent.ExecutionContext.Implicits.global

class OnlineFragment extends RouteStoryFragment with SwipingStaggeredFragment {
  lazy val number = Option(getArguments).map(_.getInt("number")).getOrElse(3)

  def viewStories(stories: List[StoryPreview]) = {
    import StoryPreviewListable._
    (grid <~ stories.listAdapterTweak) ~ (swiper <~ Tweaks.stopRefresh)
  }

  override def onStart() = {
    super.onStart()
    getLoaderManager.initLoader(0, null, loaderCallbacks)
    runUi(swiper <~ Tweaks.startRefresh)
  }

  def refresh = Ui {
    getLoaderManager.restartLoader(0, null, loaderCallbacks)
  }

  object loaderCallbacks extends LoaderManager.LoaderCallbacks[List[StoryPreview]] {
    override def onCreateLoader(id: Int, args: Bundle) =
      new ResolvableLoader(app.webApi.latest(number).map(_.stories))
    override def onLoaderReset(loader: Loader[List[StoryPreview]]) = ()
    override def onLoadFinished(loader: Loader[List[StoryPreview]], data: List[StoryPreview]) =
      runUi(viewStories(data))
  }
}
