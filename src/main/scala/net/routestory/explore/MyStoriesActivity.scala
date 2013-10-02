package net.routestory.explore

import org.ektorp.ViewQuery
import net.routestory.model._
import android.os.Bundle
import net.routestory.parts.{ FragmentPaging, FragmentDataProvider, StoryActivity }
import scala.concurrent._
import ExecutionContext.Implicits.global
import net.routestory.R
import rx._
import scala.async.Async.{ async, await }
import android.util.Log

class MyStoriesActivity extends StoryActivity
  with HazStories
  with FragmentDataProvider[HazStories]
  with FragmentPaging {

  lazy val myStories: Var[Future[List[StoryResult]]] = Var(fetchStories)
  def getStories = myStories
  def getFragmentData(tag: String): HazStories = this
  override val showControls = false

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(drawer(getTabs(
      "Stories" → ff[ResultListFragment](R.string.empty_mystories),
      "Drafts" → ff[ResultListFragment](R.string.empty_mystories),
      "Map" → ff[ResultMapFragment]()
    )))
  }

  override def onStart() {
    super.onStart()
    if (myStories.now.isCompleted) myStories.update(fetchStories)
  }

  def fetchStories = {
    val query = new ViewQuery().designDocId("_design/Story").viewName("byme").descending(true).includeDocs(true)
    async {
      Log.d("MyStories", "Fetching my stories")
      val (stories, _, _) = await(app.getQueryResults[StoryResult](remote = false, query, None))
      Log.d("MyStories", "Got stories")
      val authorIds = stories.map(_.authorId)
      val authors = await(app.getObjects[Author](authorIds))
      stories.filter(_.authorId != null) foreach { r ⇒
        r.author = authors(r.authorId)
      }
      stories
    } onFailureUi {
      case e ⇒ e.printStackTrace()
    }
  }
}
