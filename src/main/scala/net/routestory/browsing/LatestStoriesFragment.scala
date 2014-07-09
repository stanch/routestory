package net.routestory.browsing

import android.util.Log
import net.routestory.data.StoryPreview
import rx.Var

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LatestStoriesFragment extends StoriesListFragment with HazStories {
  override lazy val stories: Var[Future[List[StoryPreview]]] = Var {
    Log.d("Latest", "init")
    fetchStories
  }
  override lazy val showControls = false
  override lazy val showReload = false

  override lazy val storyteller = this
  override lazy val emptyText = "No stories lately :("
  override lazy val errorText = "Couldn’t load stories :("

  lazy val number = Option(getArguments).map(_.getInt("number")).getOrElse(3)

  override def onStart() {
    super.onStart()
    Log.d("Latest", "onStart")
    if (stories.now.isCompleted) stories.update(fetchStories)
  }

  def fetchStories = app.webApi.latest(number).go.map(_.stories)
}
