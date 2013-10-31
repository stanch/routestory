package net.routestory.explore2

import net.routestory.R
import android.view.{ LayoutInflater, Gravity, View, ViewGroup }
import android.widget.{ ListAdapter ⇒ _, _ }
import net.routestory.parts.{ FragmentData, RouteStoryFragment }
import android.support.v4.app.{ Fragment, ListFragment }
import android.app.Activity
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import rx._
import org.scaloid.common._
import android.os.Bundle
import android.content.Context
import org.macroid.contrib.Layouts.HorizontalLinearLayout
import android.util.Log
import scala.async.Async.{ async, await }
import org.macroid.contrib.ExtraTweaks
import org.macroid.contrib.ListAdapter
import net.routestory.lounge2.Puffy
import net.routestory.model2.StoryPreview
import scala.ref.WeakReference

trait HazStories {
  def getStories: Rx[Future[List[Puffy[StoryPreview]]]]
  def next() {}
  def prev() {}
  def hasNext = false
  def hasPrev = false
  val showControls = true
  val showReloadProgress = true
}

trait StoryListObserverFragment extends FragmentData[HazStories] { self: Fragment ⇒
  lazy val storyteller = getFragmentData
  lazy val stories = storyteller.getStories
  var observer: Option[Obs] = None

  def update(data: Future[List[Puffy[StoryPreview]]])

  def observe() {
    if (observer.isEmpty) {
      // create a new observer
      observer = Some(Obs(stories)(update(stories())))
    }
    // start observing
    observer.foreach(_.active = true)
  }

  def neglect() {
    // stop observing, since we go out of screen
    observer.foreach(_.active = false)
  }
}

class StoryListFragment extends ListFragment with RouteStoryFragment with StoryListObserverFragment with ExtraTweaks {
  lazy val emptyText = Option(getArguments) map {
    _.getString("emptyText")
  } getOrElse {
    getResources.getString(R.string.empty_search)
  }
  lazy val errorText = Option(getArguments) map {
    _.getString("errorText")
  } getOrElse emptyText

  var nextButton = slot[Button]
  var prevButton = slot[Button]

  var adapter: Option[StoryListFragment.Adapter] = None

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = super.onCreateView(inflater, container, savedInstanceState) ~> Bg.res(R.drawable.caspa)
    val list = findView[ListView](view, android.R.id.list) ~> (_.setDivider(null))
    if (storyteller.showControls) {
      list.get.addFooterView(l[HorizontalLinearLayout](
        w[Button] ~> text("Prev") ~> wire(prevButton) ~> On.click(storyteller.prev()),
        w[Button] ~> text("Next") ~> wire(nextButton) ~> On.click(storyteller.next())
      ) ~> (_.setGravity(Gravity.CENTER_HORIZONTAL)))
      prevButton ~> enable(storyteller.hasPrev)
      nextButton ~> enable(storyteller.hasNext)
    }
    view
  }

  override def onStart() {
    super.onStart()
    Option(getListAdapter) orElse adapter.map(setListAdapter)
    observe()
  }

  override def onStop() {
    super.onStop()
    neglect()
  }

  def update(data: Future[List[Puffy[StoryPreview]]]) = async {
    if (storyteller.showReloadProgress || Option(getListAdapter).exists(_.isEmpty)) await(Ui(setListShown(false)))
    Log.d("StoryList", s"Received future $data from $observer")
    val s = await(data mapUi {
      x ⇒ setEmptyText(emptyText); x
    } recoverUi {
      case t if !t.isInstanceOf[UninitializedError] ⇒ t.printStackTrace(); setEmptyText(errorText); Nil
    })
    Ui {
      adapter map { a ⇒
        a.clear()
      } getOrElse {
        adapter = Some(StoryListFragment.Adapter(WeakReference(getActivity)))
        setListAdapter(adapter.get)
      }
      adapter.map(_.addAll(s))
      setListShown(true)
    }
    prevButton ~> enable(storyteller.hasPrev)
    nextButton ~> enable(storyteller.hasNext)
  }
}

object StoryListFragment {
  case class Adapter(activity: WeakReference[Activity])(implicit ctx: Context) extends ListAdapter[Puffy[StoryPreview], View] {
    def makeView = PreviewRow.makeView
    def fillView(view: View, parent: ViewGroup, data: Puffy[StoryPreview]) =
      PreviewRow.fillView(view, parent.getMeasuredWidth, data, activity)
    override def isEnabled(position: Int) = false
  }
}