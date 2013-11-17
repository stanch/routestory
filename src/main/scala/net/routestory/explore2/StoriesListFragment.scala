package net.routestory.explore2

import net.routestory.R
import android.view.{ LayoutInflater, Gravity, View, ViewGroup }
import android.widget.{ ListAdapter ⇒ _, _ }
import net.routestory.parts.RouteStoryFragment
import android.support.v4.app.ListFragment
import android.app.Activity
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
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
import org.macroid.MediaQueries

class StoriesListFragment extends ListFragment
  with RouteStoryFragment
  with StoriesObserverFragment
  with ExtraTweaks {

  lazy val emptyText = Option(getArguments) map {
    _.getString("emptyText")
  } getOrElse {
    getResources.getString(R.string.empty_search)
  }

  lazy val errorText = Option(getArguments) map {
    _.getString("errorText")
  } getOrElse emptyText

  lazy val showControls = Option(getArguments).exists(_.getBoolean("showControls", false))
  lazy val showReload = Option(getArguments).exists(_.getBoolean("showReload", false))

  var nextButton = slot[Button]
  var prevButton = slot[Button]

  var adapter: Option[StoriesListFragment.Adapter] = None

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = super.onCreateView(inflater, container, savedInstanceState) ~> Bg.res(R.drawable.caspa)
    val list = findView[ListView](view, android.R.id.list) ~> (_.setDivider(null))
    if (showControls) {
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

  def update(data: Future[HazStories#Stories]) = async {
    if (showReload || Option(getListAdapter).exists(_.isEmpty)) await(Ui(setListShown(false)))
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
        adapter = Some(StoriesListFragment.Adapter(WeakReference(getActivity)))
        setListAdapter(adapter.get)
      }
      adapter.map(_.addAll(s))
      setListShown(true)
    }
    prevButton ~> enable(storyteller.hasPrev)
    nextButton ~> enable(storyteller.hasNext)
  }
}

object StoriesListFragment {
  case class Adapter(activity: WeakReference[Activity])(implicit ctx: Context) extends ListAdapter[Puffy[StoryPreview], View] with MediaQueries {
    def makeView = PreviewRow.makeView
    def fillView(view: View, parent: ViewGroup, data: Puffy[StoryPreview]) =
      PreviewRow.fillView(view, parent.getMeasuredWidth, data, activity)
    override def isEnabled(position: Int) = false
  }
}