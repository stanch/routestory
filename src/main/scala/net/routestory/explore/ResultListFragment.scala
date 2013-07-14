package net.routestory.explore

import net.routestory.R
import net.routestory.model.StoryResult
import android.content.Context
import android.os.Bundle
import android.view.{ Gravity, LayoutInflater, View, ViewGroup }
import android.widget._
import net.routestory.parts.StoryFragment
import android.app.{ ListFragment, Activity }
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import rx._
import org.scaloid.common._
import scala.Some

class ResultListFragment extends ListFragment with StoryFragment {
    lazy val storyteller = getActivity.asInstanceOf[HazStories]
    lazy val stories = storyteller.getStories
    lazy val observe = Obs(stories) {
        update(stories())
    }

    lazy val defaultEmptyText = getResources.getString(R.string.empty_search)
    var emptyText: Option[String] = None

    var next: Button = _
    var prev: Button = _

    def tweakEmptyText(text: String) {
        emptyText = Some(text)
    }

    override def onFirstStart() {
        findView[ListView](android.R.id.list).addFooterView(new LinearLayout(ctx) {
            setOrientation(LinearLayout.HORIZONTAL)
            setGravity(Gravity.CENTER_HORIZONTAL)
            prev = new Button(ctx) {
                setText("Prev")
                setOnClickListener(storyteller.prev())
            }
            this += prev
            next = new Button(ctx) {
                setText("Next")
                setOnClickListener(storyteller.next())
            }
            this += next
        })
        setEmptyText(emptyText.getOrElse(defaultEmptyText))
    }

    override def onEveryStart() {
        observe.active = true
    }

    override def onDestroyView() {
        super.onDestroyView()
        observe.active = false
    }

    def update(results: Future[List[StoryResult]]) {
        runOnUiThread(setListShown(false))
        results.onSuccessUi {
            case res ⇒
                setListAdapter(new ResultListFragment.StoryListAdapter(ctx, res))
                prev.setEnabled(storyteller.hasPrev)
                next.setEnabled(storyteller.hasNext)
                setListShown(true)
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