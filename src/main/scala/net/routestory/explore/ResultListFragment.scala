package net.routestory.explore

import net.routestory.R
import net.routestory.model.StoryResult
import android.view.{ LayoutInflater, Gravity, View, ViewGroup }
import android.widget._
import net.routestory.parts.{ FragmentData, StoryFragment }
import android.app.{ ListFragment, Activity }
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import rx._
import org.scaloid.common._
import android.os.Bundle
import android.content.Context

class ResultListFragment extends ListFragment with StoryFragment with FragmentData[HazStories] {
  lazy val storyteller = getFragmentData
  lazy val stories = storyteller.getStories
  var observer: Obs = _

  lazy val emptyText = Option(getArguments) map {
    _.getString("emptyText")
  } getOrElse {
    getResources.getString(R.string.empty_search)
  }

  def next = findView[Button](Id.next)
  def prev = findView[Button](Id.prev)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = super.onCreateView(inflater, container, savedInstanceState)
    view.findViewById(android.R.id.list).asInstanceOf[ListView].addFooterView(new HorizontalLinearLayout(ctx) {
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
    view
  }

  override def onStart() {
    super.onStart()
    setEmptyText(emptyText)
    observer = Obs(stories) {
      update(stories())
    }
  }

  override def onDestroyView() {
    super.onDestroyView()
    observer.active = false
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
  def newInstance(emptyText: String) = {
    val frag = new ResultListFragment
    val bundle = new Bundle
    bundle.putString("emptyText", emptyText)
    frag.setArguments(bundle)
    frag
  }

  def newInstance(emptyText: Int)(implicit ctx: Context) = {
    val frag = new ResultListFragment
    val bundle = new Bundle
    bundle.putString("emptyText", ctx.getResources.getString(emptyText))
    frag.setArguments(bundle)
    frag
  }

  class StoryListAdapter(results: List[StoryResult])(implicit ctx: Activity) extends ArrayAdapter(ctx, R.layout.storylist_row, results) {
    override def getView(position: Int, itemView: View, parent: ViewGroup): View = {
      val view = ResultRow.getView(itemView, parent.getMeasuredWidth, getItem(position), ctx)
      view.setPaddingRelative(8 dip, view.getPaddingTop, 8 dip, view.getPaddingBottom)
      view
    }
    override def isEnabled(position: Int): Boolean = false
  }
}