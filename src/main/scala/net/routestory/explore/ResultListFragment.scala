package net.routestory.explore

import net.routestory.R
import net.routestory.model.StoryResult
import android.view.{ Gravity, View, ViewGroup }
import android.widget._
import net.routestory.parts.StoryFragment
import android.app.{ ListFragment, Activity }
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import rx._
import scala.Some
import org.scaloid.common._

class ResultListFragment(storyteller: HazStories) extends ListFragment with StoryFragment {
  def stories = storyteller.getStories
  lazy val observe = Obs(stories) {
    update(stories())
  }

  lazy val defaultEmptyText = getResources.getString(R.string.empty_search)
  var emptyText: Option[String] = None

  lazy val next = findView[Button](Id.next)
  lazy val prev = findView[Button](Id.prev)

  def tweakEmptyText(text: String) {
    emptyText = Some(text)
  }

  override def onFirstStart() {
    findView[ListView](android.R.id.list).addFooterView(new HorizontalLinearLayout(ctx) {
      setGravity(Gravity.CENTER_HORIZONTAL)
      this += new Button(ctx) {
        setId(Id.prev)
        setText("Prev")
        setOnClickListener(storyteller.prev())
      }
      this += new Button(ctx) {
        setId(Id.next)
        setText("Next")
        setOnClickListener(storyteller.next())
      }
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
        setListAdapter(new ResultListFragment.StoryListAdapter(res))
        prev.setEnabled(storyteller.hasPrev)
        next.setEnabled(storyteller.hasNext)
        setListShown(true)
    }
  }
}

object ResultListFragment {
  class StoryListAdapter(results: List[StoryResult])(implicit ctx: Activity) extends ArrayAdapter(ctx, R.layout.storylist_row, results) {
    override def getView(position: Int, itemView: View, parent: ViewGroup): View = {
      val view = ResultRow.getView(itemView, parent.getMeasuredWidth, getItem(position), ctx)
      view.setPaddingRelative(8 dip, view.getPaddingTop, 8 dip, view.getPaddingBottom)
      view
    }
    override def isEnabled(position: Int): Boolean = false
  }
}