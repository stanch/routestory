package net.routestory.explore

import org.ektorp.ViewQuery
import net.routestory.model._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.async.Async.{ async, await }
import rx.Var
import android.util.Log

class LatestFragment extends net.routestory.explore2.StoryListFragment with net.routestory.explore2.HazStories {
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

  def fetchStories = net.routestory.lounge2.Lounge.getLatestStories

  //  def fetchStories = async {
  //    Log.d("Latest", "Fetching latest stories")
  //    val query = new ViewQuery().designDocId("_design/Story").viewName("byTimestamp").descending(true).limit(number)
  //    val (stories, _, _) = await(app.getQueryResults[StoryResult](remote = true, query, None))
  //    val authors = await(app.getObjects[Author](stories.filter(_.authorId != null).map(_.authorId)))
  //    stories.filter(_.authorId != null).foreach(s ⇒ s.author = authors(s.authorId))
  //    stories
  //  }
}
