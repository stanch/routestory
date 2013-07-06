package net.routestory.explore

import net.routestory.R
import net.routestory.model.StoryResult
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import net.routestory.parts.StoryFragment
import com.actionbarsherlock.app.SherlockListFragment
import android.app.Activity
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ResultListFragment extends SherlockListFragment with StoryFragment with ViewzResults {
	lazy val defaultEmptyText = ctx.getResources.getString(R.string.empty_search)
    var emptyText: Option[String] = None
	
	override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
    }

    def tweakEmptyText(text: String) {
        emptyText = Some(text)
    }
	
	override def onStart() {
		super.onStart()
        setEmptyText(emptyText.getOrElse(defaultEmptyText))
    }
    
    def update(results: Future[List[StoryResult]]) {
    	results.onSuccessUI { case res ⇒
    		setListAdapter(new ResultListFragment.StoryListAdapter(ctx, res))
    	}
    }
}

object ResultListFragment {
	class StoryListAdapter(ctx: Activity, results: List[StoryResult]) extends ArrayAdapter(ctx, R.layout.storylist_row, results) {
        override def getView(position: Int, itemView: View, parent: ViewGroup): View = {
            ResultRow.getView(itemView, parent.getMeasuredWidth, getItem(position), ctx)
		}
        override def isEnabled(position: Int): Boolean = false
    }
}