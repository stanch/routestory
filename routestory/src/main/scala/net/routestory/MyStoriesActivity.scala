package net.routestory

import org.ektorp.ViewQuery
import net.routestory.model._
import net.routestory.parts.TabListener
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.widget.FrameLayout
import com.actionbarsherlock.app.ActionBar
import com.actionbarsherlock.app.SherlockFragmentActivity
import com.actionbarsherlock.view.MenuItem
import net.routestory.parts.StoryActivity
import org.scaloid.common._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import net.routestory.explore.ResultListFragment
import akka.dataflow._
import net.routestory.parts.TabListener2

class MyStoriesActivity extends SherlockFragmentActivity with StoryActivity {
	var activeTab: ResultListFragment = null
	var stories: Future[List[StoryResult]] = Future.failed(new UninitializedError)
	
    override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(new FrameLayout(ctx))
        
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)
	    bar.setDisplayShowHomeEnabled(true)
	    bar.setDisplayHomeAsUpEnabled(true)
	    
	    val sel = if (savedInstanceState!=null) savedInstanceState.getInt("tab") else 0
	
	    bar.addTab(
    		bar.newTab().setText(R.string.title_tab_byme).setTabListener(TabListener2[ResultListFragment](this, "byme")),
    		0, 0==sel
		)
        bar.addTab(
    		bar.newTab().setText(R.string.title_tab_saved).setTabListener(TabListener2[ResultListFragment](this, "saved")),
    		1, 1==sel
		)
    }
    
    override def onSaveInstanceState(savedInstanceState: Bundle) {
    	super.onSaveInstanceState(savedInstanceState)
    	savedInstanceState.putInt("tab", bar.getSelectedTab.getPosition)
    }
    
    def showStories() {
        val query = new ViewQuery().designDocId("_design/Story").viewName(activeTab.getTag).descending(true).includeDocs(true)
        flow {
            stories = app.getQueryResults[StoryResult](remote = false, query)
            val authorIds = await(stories).map(_.authorId)
            val authors = await(app.getObjects[Author](authorIds))
            await(stories).filter(_.authorId!=null) foreach { r ⇒
                r.author = authors(r.authorId)
            }
            activeTab.update(stories)
        }
    }
    
    override def onAttachFragment(fragment: Fragment) {
    	activeTab = fragment.asInstanceOf[ResultListFragment]
//		if (fragment.getTag().equals("byme")) {
//			activeTab.reallySetEmptyText(getResources().getString(R.string.empty_mystories));
//		} else {
//			activeTab.reallySetEmptyText(getResources().getString(R.string.empty_savedstories));
//		}
		showStories()
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
}
