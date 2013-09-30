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
import org.macroid.util.Text

class MyStoriesActivity extends StoryActivity
  with HazStories
  with FragmentDataProvider[HazStories]
  with FragmentPaging {

  lazy val myStories: Var[Future[List[StoryResult]]] = Var(fetchStories)
  def getStories = myStories
  def getFragmentData(tag: String): HazStories = this

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(drawer(getTabs(
      Text(R.string.title_tab_resultslist) → ff[ResultListFragment](R.string.empty_mystories),
      Text(R.string.title_tab_resultsmap) → ff[ResultMapFragment]()
    )))
  }

  def fetchStories = {
    val query = new ViewQuery().designDocId("_design/Story").viewName("byme").descending(true).includeDocs(true)
    async {
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
