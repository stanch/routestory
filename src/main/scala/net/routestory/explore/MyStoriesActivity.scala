package net.routestory.explore

import org.ektorp.ViewQuery
import net.routestory.model._
import android.os.Bundle
import android.widget.FrameLayout
import net.routestory.parts.{ FragmentDataProvider, StoryActivity }
import org.scaloid.common._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import net.routestory.R
import rx._
import android.app.ActionBar
import scala.async.Async.{ async, await }
import android.util.Log

class MyStoriesActivity extends StoryActivity with HazStories with FragmentDataProvider[HazStories] {
  lazy val myStories: Var[Future[List[StoryResult]]] = Var(fetchStories)
  def getStories = myStories
  def getFragmentData(tag: String): HazStories = this

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(new FrameLayout(ctx))

    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)
    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)

    val sel = Option(savedInstanceState).map(_.getInt("tab")).getOrElse(0)
    bar.addTab(R.string.title_tab_resultslist, ResultListFragment.newInstance(R.string.empty_mystories), Tag.list, sel == 0)
    bar.addTab(R.string.title_tab_resultsmap, new ResultMapFragment, Tag.map, sel == 1)
  }

  override def onSaveInstanceState(savedInstanceState: Bundle) {
    super.onSaveInstanceState(savedInstanceState)
    savedInstanceState.putInt("tab", bar.getSelectedTab.getPosition)
  }

  def fetchStories = {
    val query = new ViewQuery().designDocId("_design/Story").viewName("byme").descending(true).includeDocs(true)
    async {
      val (stories, _, _) = await(app.getQueryResults[StoryResult](remote = false, query, None))
      Log.d("Story", "got stories " + System.currentTimeMillis.toString)
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
