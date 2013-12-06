package net.routestory.explore

import rx.Var
import android.util.Log
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.model2.StoryPreview
import net.routestory.needs.NeedLatest

class LatestFragment extends StoriesListFragment with HazStories {
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

  def fetchStories = NeedLatest(number).go.map(_.stories)
}
