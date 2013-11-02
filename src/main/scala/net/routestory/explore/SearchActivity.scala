package net.routestory.explore

import org.ektorp.ViewQuery

import net.routestory.R
import net.routestory.model.Author
import net.routestory.model.StoryResult
import net.routestory.parts.{ FragmentPaging, FragmentDataProvider, GotoDialogFragments, RouteStoryActivity }
import android.app.{ ActionBar, SearchManager }
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.{ Menu, MenuItem, View }
import android.widget.{ FrameLayout, SearchView }

import scala.concurrent._
import ExecutionContext.Implicits.global
import rx._
import scala.util.Try
import scala.async.Async.{ async, await }
import org.macroid.util.{ Thunk, Text }

trait HazStories {
  def getStories: Rx[Future[List[StoryResult]]]
  def next() {}
  def prev() {}
  def hasNext = false
  def hasPrev = false
  val showControls = true
  val showReloadProgress = true
}

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
  with FragmentPaging {
  import SearchActivity._

  // TODO: see if this can be made easier...

  // a function, producing equal queries
  // we use a factory here, so that we can choose
  // to add queryParams("bookmark", ...) or not
  // if we used a mutable object instead, the old queryParam would remain
  // and we would not be able to get back to the first page by setting bookmark=None
  type QueryFactory = Thunk[ViewQuery]
  val queryFactory: Var[Option[QueryFactory]] = Var(None)

  // this is failed at the beginning, so that observers don’t update (they update onSuccess)
  val searchResults: Var[Future[List[StoryResult]]] = Var(Future.failed(new UninitializedError))

  // a stack of page bookmarks
  var totalStories = 0
  val storiesByPage = 5
  var bookmarks: List[String] = List()

  // when a factory is switched, clear bookmarks and show the first page
  Obs(queryFactory) {
    bookmarks = List()
    next()
  }

  def getStories = searchResults
  def getFragmentData(tag: String): HazStories = this
  override def next() = queryFactory() foreach { q ⇒
    searchResults() = fetchResults(q, bookmarks.headOption)
  }
  override def prev() = queryFactory() foreach { q ⇒
    bookmarks = Try(bookmarks.tail.tail).getOrElse(List()) // remove bookmarks to next and current pages
    searchResults() = fetchResults(q, bookmarks.headOption)
  }
  override def hasNext = storiesByPage * bookmarks.length < totalStories
  override def hasPrev = bookmarks.length > 1

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(drawer(getTabs(
      "Results" → f[StoryListFragment].pass(
        "emptyText" → "Couldn’t find anything :(",
        "errorText" → "Error occured :("
      ).factory,
      "Map" → f[ResultMapFragment].factory
    )))
  }

  override def onStart() {
    super.onStart()

    getIntent match {
      case SearchIntent(q) ⇒
        bar.setSubtitle(getResources.getString(R.string.search_results_for) + " " + q)
        textQuery(s"title:($q~) tags:($q~)") // TODO: (a~ b~)
      case TagIntent(t) ⇒
        bar.setSubtitle(getResources.getString(R.string.search_results_tag) + " " + t)
        textQuery(s"""tags:"$t" """)
      case BboxIntent(bbox) ⇒
        bar.setSubtitle(getResources.getString(R.string.search_results_area))
        geoQuery(bbox)
      case _ ⇒
        bar.setSubtitle("Search results for: lisbon")
        textQuery("tags:lisbon")
    }
  }

  private def fetchResults(queryFactory: QueryFactory, bookmark: Option[String]) = async {
    val (results, total, mark) = await(app.getQueryResults[StoryResult](remote = true, queryFactory(), bookmark))
    bookmarks ::= mark
    totalStories = total
    val authorIds = results.map(_.authorId)
    val authors = await(app.getObjects[Author](authorIds))
    results.filter(_.authorId != null) foreach { r ⇒
      r.author = authors(r.authorId)
    }
    results
  }

  def textQuery(q: String) {
    queryFactory() = Some(Thunk {
      (new ViewQuery).designDocId("_design/Story").viewName("textQuery")
        .queryParam("q", q).limit(storiesByPage).queryParam("include_geometry", "true")
    })
  }

  def geoQuery(bbox: String) {
    bar.setSubtitle(getResources.getString(R.string.search_results_area))
    queryFactory() = Some(Thunk {
      (new ViewQuery).designDocId("_design/Story").viewName("geoQuery")
        .queryParam("bbox", bbox)
    })
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.activity_search_results, menu)
    val searchManager = getSystemService(Context.SEARCH_SERVICE).asInstanceOf[SearchManager]
    val searchMenuItem = menu.findItem(R.id.search)
    val searchView = searchMenuItem.getActionView.asInstanceOf[SearchView]
    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName))
    searchView.setIconifiedByDefault(false)
    searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener {
      override def onFocusChange(view: View, queryTextFocused: Boolean) {
        if (!queryTextFocused) {
          searchMenuItem.collapseActionView()
        }
      }
    })
    true
  }
}