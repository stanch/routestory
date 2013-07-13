package net.routestory.explore

import org.ektorp.ViewQuery
import net.routestory.model._
import android.content.Intent
import android.os.Bundle
import android.app.Fragment
import android.widget.FrameLayout
import net.routestory.parts.StoryActivity
import org.scaloid.common._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import akka.dataflow._
import net.routestory.parts.TabListener
import net.routestory.{MainActivity, R}
import rx._
import android.view.MenuItem
import android.app.ActionBar

class MyStoriesActivity extends StoryActivity with HazStories {
	var activeTab: ResultListFragment = null
    val myStories: Var[Future[List[StoryResult]]] = Var(Future.failed(new UninitializedError))

    def getStories = myStories

    override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(new FrameLayout(ctx))
        
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)
	    bar.setDisplayShowHomeEnabled(true)
	    bar.setDisplayHomeAsUpEnabled(true)
	    
	    val sel = Option(savedInstanceState) map (_.getInt("tab")) getOrElse 0
        List(
            R.string.title_tab_byme → TabListener[ResultListFragment](this, "byme"),
            R.string.title_tab_saved → TabListener[ResultListFragment](this, "saved")
        ).zipWithIndex.foreach { case ((title, tabListener), n) ⇒
            bar.addTab(bar.newTab().setText(title).setTabListener(tabListener), n, n == sel)
        }
    }
    
    override def onSaveInstanceState(savedInstanceState: Bundle) {
    	super.onSaveInstanceState(savedInstanceState)
    	savedInstanceState.putInt("tab", bar.getSelectedTab.getPosition)
    }
    
    def showStories() {
        val query = new ViewQuery().designDocId("_design/Story").viewName(activeTab.getTag).descending(true).includeDocs(true)
        myStories.update(flow {
            val (stories, _, _) = await(app.getQueryResults[StoryResult](remote = false, query, None))
            val authorIds = stories.map(_.authorId)
            val authors = await(app.getObjects[Author](authorIds))
            stories.filter(_.authorId!=null) foreach { r ⇒
                r.author = authors(r.authorId)
            }
            stories
        })
    }
    
    override def onAttachFragment(fragment: Fragment) {
    	activeTab = fragment.asInstanceOf[ResultListFragment]
		if (fragment.getTag.equals("byme")) {
			activeTab.tweakEmptyText(getResources.getString(R.string.empty_mystories))
		} else {
			activeTab.tweakEmptyText(getResources.getString(R.string.empty_savedstories))
		}
		showStories()
	}
    
    override def onOptionsItemSelected(item: MenuItem): Boolean = {
        item.getItemId match {
            case android.R.id.home ⇒ {
                val intent = SIntent[MainActivity]
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                true
            }
            case _ ⇒ super.onOptionsItemSelected(item)
        }
    }
}
