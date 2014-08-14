package net.routestory.browsing.stories

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import macroid.FullDsl._
import macroid.IdGeneration
import net.routestory.R
import net.routestory.data.StoryPreview
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import rx.Var

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object SearchActivity {
  object SearchIntent {
    def unapply(i: Intent): Option[String] = if (i.getAction == Intent.ACTION_SEARCH) {
      Some(i.getStringExtra(SearchManager.QUERY))
    } else None
  }
  object TagIntent {
    def unapply(i: Intent): Option[String] = if (i.hasExtra("tag")) Some(i.getStringExtra("tag")) else None
  }
  object BboxIntent {
    def unapply(i: Intent): Option[String] = if (i.hasExtra("bbox")) Some(i.getStringExtra("bbox")) else None
  }
}

class SearchActivity extends RouteStoryActivity
  with FragmentPaging
  with IdGeneration {
  import SearchActivity._

  // this is failed at the beginning, so that observers don’t update (they update onSuccess)
  val stories: Var[Future[List[StoryPreview]]] = Var(Future.failed(new UninitializedError))

  // a stack of page bookmarks
  var totalStories = 0
  val storiesByPage = 5
  var bookmarks = List.empty[String]

  lazy val query = getIntent match {
    case SearchIntent(q) ⇒
      bar.setSubtitle(getResources.getText(R.string.search_results_for) + " " + q)
      textQuery(q)
    case TagIntent(t) ⇒
      bar.setSubtitle(getResources.getText(R.string.search_results_tag) + " " + t)
      tagQuery(t)
    case _ ⇒
      bar.setSubtitle("Search results for: lisbon")
      tagQuery("lisbon")
  }

  def textQuery(q: String) = { bookmark: Option[String] ⇒
    app.webApi.search(q, storiesByPage, bookmark).go
  }

  def tagQuery(tag: String) = { bookmark: Option[String] ⇒
    app.webApi.tagged(tag, storiesByPage, bookmark).go
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(getUi(drawer(getTabs(
      "Results" → f[OnlineFragment].factory
    ))))
  }

  override def onStart() {
    super.onStart()
    bookmarks = List.empty
  }

  def fetchResults(bookmark: Option[String]) = async {
    val results = await(query(bookmark))
    bookmarks ::= results.bookmark
    totalStories = results.total
    results.stories
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.activity_search_results, menu)
    setupSearch(menu)
    true
  }
}