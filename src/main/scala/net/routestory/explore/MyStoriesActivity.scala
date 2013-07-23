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
import akka.dataflow._
import net.routestory.R
import rx._
import android.app.ActionBar

class MyStoriesActivity extends StoryActivity with FragmentDataProvider[HazStories] {
  lazy val myStories = new HazStories {
    val s: Var[Future[List[StoryResult]]] = Var(fetchStories("byme"))
    def getStories = s
  }
  lazy val savedStories = new HazStories {
    val s: Var[Future[List[StoryResult]]] = Var(fetchStories("saved"))
    def getStories = s
  }
  def getFragmentData(tag: String): HazStories = if (tag == Tag.my) myStories else savedStories

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(new FrameLayout(ctx))

    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)
    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)

    val sel = Option(savedInstanceState).map(_.getInt("tab")).getOrElse(0)
    bar.addTab(R.string.title_tab_byme, ResultListFragment.newInstance(R.string.empty_mystories), Tag.my, sel == 0)
    bar.addTab(R.string.title_tab_saved, ResultListFragment.newInstance(R.string.empty_savedstories), Tag.saved, sel == 1)
  }

  override def onSaveInstanceState(savedInstanceState: Bundle) {
    super.onSaveInstanceState(savedInstanceState)
    savedInstanceState.putInt("tab", bar.getSelectedTab.getPosition)
  }

  def fetchStories(view: String) = {
    val query = new ViewQuery().designDocId("_design/Story").viewName(view).descending(true).includeDocs(true)
    flow {
      val (stories, _, _) = await(app.getQueryResults[StoryResult](remote = false, query, None))
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
