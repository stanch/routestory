package net.routestory.explore

import scala.Some
import scala.async.Async._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu

import org.macroid.LayoutDsl._
import rx.Var

import net.routestory.R
import net.routestory.model.StoryPreview
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import net.routestory.util.FragmentDataProvider
import org.macroid.IdGeneration

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
  with HazStories
  with FragmentDataProvider[HazStories]
  with FragmentPaging
  with IdGeneration {
  import SearchActivity._

  // this is failed at the beginning, so that observers don’t update (they update onSuccess)
  val stories: Var[Future[List[StoryPreview]]] = Var(Future.failed(new UninitializedError))
  def getFragmentData(tag: String): HazStories = this

  // a stack of page bookmarks
  var totalStories = 0
  val storiesByPage = 5
  var bookmarks = List.empty[String]

  // pagination
  override def next() {
    stories.update(fetchResults(bookmarks.headOption))
  }
  override def prev() {
    // remove bookmarks to next and current pages
    bookmarks = Try(bookmarks.tail.tail).getOrElse(List())
    stories.update(fetchResults(bookmarks.headOption))
  }
  override def hasNext = storiesByPage * bookmarks.length < totalStories
  override def hasPrev = bookmarks.length > 1

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
    app.api.NeedSearch(q, storiesByPage, bookmark).go
  }

  def tagQuery(tag: String) = { bookmark: Option[String] ⇒
    app.api.NeedTagged(tag, storiesByPage, bookmark).go
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(drawer(getTabs(
      "Results" → f[StoriesListFragment].pass(
        "emptyText" → "Couldn’t find anything :(",
        "errorText" → "Error occured :(",
        "showControls" → true,
        "showReload" → true
      ).factory
    )))
  }

  override def onStart() {
    super.onStart()
    bookmarks = List.empty
    next()
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