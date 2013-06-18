package net.routestory.explore

import scala.ref.WeakReference

import org.ektorp.ViewQuery

import net.routestory.MainActivity
import net.routestory.R
import net.routestory.model.Author
import net.routestory.model.StoryResult
import net.routestory.parts.GotoDialogFragments
import net.routestory.parts.TabListener
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.View
import android.widget.FrameLayout

import com.actionbarsherlock.app.ActionBar
import com.actionbarsherlock.app.SherlockFragmentActivity
import com.actionbarsherlock.view.Menu
import com.actionbarsherlock.view.MenuItem
import com.actionbarsherlock.widget.SearchView

import net.routestory.parts.StoryActivity
import scala.concurrent._
import ExecutionContext.Implicits.global
import org.scaloid.common._
import akka.dataflow._

trait HazResults {
	def getResults: Future[List[StoryResult]]
}

trait ViewzResults {
	def update()
}

object SearchResultsActivity {
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

class SearchResultsActivity extends SherlockFragmentActivity with StoryActivity with HazResults {
    import SearchResultsActivity._

	var searchResults: Future[List[StoryResult]] = Future.successful(List())
	var viewers = List[WeakReference[ViewzResults]]()
	
	override def getResults = searchResults
	
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
	    setContentView(new FrameLayout(ctx))
	    
	    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)
	    bar.setDisplayShowHomeEnabled(true)
	    bar.setDisplayHomeAsUpEnabled(true)
	    
	    val showMap = getIntent.hasExtra("showmap")
	    bar.addTab(
    		bar.newTab().setText(R.string.title_tab_resultslist).setTabListener(new TabListener(this, "list", classOf[ResultListFragment])),
    		0, !showMap
		)
	    bar.addTab(
    		bar.newTab().setText(R.string.title_tab_resultsmap).setTabListener(new TabListener(this, "map", classOf[ResultMapFragment])),
    		1, showMap
		)
	}
	
	override def onStart() {
		super.onStart()
		if (!GotoDialogFragments.ensureNetwork(this)) {
			return
		}
		
		getIntent match {
            case SearchIntent(query) ⇒
                bar.setSubtitle(getResources.getString(R.string.search_results_for) + " " + query)
                textQuery(s"title:($query~) tags:($query~)") // TODO: (a~ b~)
            case TagIntent(tag) ⇒
                bar.setSubtitle(getResources.getString(R.string.search_results_tag) + " " + tag)
                textQuery(s"""tags:"$tag"""")
            case BboxIntent(bbox) ⇒
                bar.setSubtitle(getResources.getString(R.string.search_results_area))
                geoQuery(bbox)
            case _ ⇒
                bar.setSubtitle("Search results for: lisbon")
                textQuery("tags:lisbon")
        }
	}
	
	private def fetchResults(query: ViewQuery): Future[List[StoryResult]] = flow {
		val results = app.getQueryResults[StoryResult](remote = true, query).apply()
		val authorIds = results.map(_.authorId)
		val authors = app.getObjects[Author](authorIds).apply()
		results.filter(_.authorId!=null) foreach { r =>
			r.author = authors(r.authorId)
		}
		results
	}
	
	def textQuery(q: String) {
        val query = new ViewQuery().designDocId("_design/Story").viewName("textQuery").queryParam("q", q).queryParam("include_geometry", "true")
		searchResults = fetchResults(query)
		viewers.flatMap(_.get).foreach(_.update())
	}
	
	def geoQuery(bbox: String) {
		getSupportActionBar.setSubtitle(getResources.getString(R.string.search_results_area))
        val query = new ViewQuery().designDocId("_design/Story").viewName("geoQuery").queryParam("bbox", bbox)
        searchResults = fetchResults(query)
		viewers.flatMap(_.get).foreach(_.update())
	}
	
	override def onCreateOptionsMenu(menu: Menu): Boolean = {
        getSupportMenuInflater.inflate(R.menu.activity_search_results, menu)
        val searchManager = getSystemService(Context.SEARCH_SERVICE).asInstanceOf[SearchManager]
        val searchMenuItem = menu.findItem(R.id.search)
        val searchView = searchMenuItem.getActionView.asInstanceOf[SearchView]
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName))
        searchView.setIconifiedByDefault(false)
        searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
            override def onFocusChange(view: View, queryTextFocused: Boolean) {
                if(!queryTextFocused) {
                    searchMenuItem.collapseActionView()
                }
            }
        })
        true
	}
	
	override def onOptionsItemSelected(item: MenuItem): Boolean = {
	    item.getItemId match {
	        case android.R.id.home => {
	            val intent = SIntent[MainActivity]
	            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
	            startActivity(intent)
	            true
	        }
	        case _ => super.onOptionsItemSelected(item)
	    }
	}
	
	override def onAttachFragment(fragment: Fragment) {
		try {
			viewers = new WeakReference[ViewzResults](fragment.asInstanceOf[ViewzResults]) :: viewers
			fragment.asInstanceOf[ViewzResults].update()
		} catch {
			case e: Throwable => e.printStackTrace()
		}
	}
}