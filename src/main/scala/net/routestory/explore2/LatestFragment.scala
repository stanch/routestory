package net.routestory.explore2

import rx.Var
import android.util.Log
import net.routestory.lounge2.Lounge

class LatestFragment extends StoryListFragment with HazStories {
  lazy val latestStories = Var {
    Log.d("Latest", "init")
    fetchStories
  }
  def getStories = latestStories
  override val showControls = false
  override val showReloadProgress: Boolean = false

  override lazy val storyteller = this
  override lazy val emptyText = "No stories lately :("
  override lazy val errorText = "Couldn’t load stories :("

  lazy val number = Option(getArguments).map(_.getInt("number")).getOrElse(3)

  override def onStart() {
    super.onStart()
    Log.d("Latest", "onStart")
    if (latestStories.now.isCompleted) latestStories.update(fetchStories)
  }

  def fetchStories = Lounge.getLatestStories
}
