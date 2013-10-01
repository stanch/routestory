package net.routestory.explore

import net.routestory.R
import net.routestory.model.StoryResult
import android.view.{ LayoutInflater, Gravity, View, ViewGroup }
import android.widget._
import net.routestory.parts.{ FragmentData, StoryFragment }
import android.support.v4.app.ListFragment
import android.app.Activity
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import rx._
import org.scaloid.common._
import android.os.Bundle
import android.content.Context
import org.macroid.contrib.Layouts.HorizontalLinearLayout

class ResultListFragment extends ListFragment with StoryFragment with FragmentData[HazStories] {
  lazy val storyteller = getFragmentData
  lazy val stories = storyteller.getStories
  var observer: Obs = _

  lazy val emptyText = Option(getArguments) map {
    _.getString("emptyText")
  } getOrElse {
    getResources.getString(R.string.empty_search)
  }

  var nextButton = slot[Button]
  var prevButton = slot[Button]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = super.onCreateView(inflater, container, savedInstanceState)
    val list = findView[ListView](view, android.R.id.list) ~> (_.setDivider(null))
    if (storyteller.showControls) {
      list.get.addFooterView(l[HorizontalLinearLayout](
        w[Button] ~> text("Prev") ~> wire(prevButton) ~> On.click(storyteller.prev()),
        w[Button] ~> text("Next") ~> wire(nextButton) ~> On.click(storyteller.next())
      ) ~> (_.setGravity(Gravity.CENTER_HORIZONTAL)))
    }
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
        setListAdapter(new ResultListFragment.StoryListAdapter(res, getActivity))
        prevButton ~> enable(storyteller.hasPrev)
        nextButton ~> enable(storyteller.hasNext)
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

  class StoryListAdapter(results: List[StoryResult], activity: Activity)(implicit ctx: Context) extends ArrayAdapter(ctx, 0, results) {
    override def getView(position: Int, itemView: View, parent: ViewGroup): View = {
      val view = ResultRow.getView(Option(itemView), parent.getMeasuredWidth, getItem(position), activity)
      view.setPaddingRelative(0, view.getPaddingTop, 8 dip, view.getPaddingBottom)
      view
    }
    override def isEnabled(position: Int): Boolean = false
  }
}