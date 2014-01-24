package net.routestory.explore

import scala.async.Async.{ async, await }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

import android.os.Bundle
import android.support.v4.app.ListFragment
import android.util.Log
import android.view.{ Gravity, LayoutInflater, View, ViewGroup }
import android.widget.{ ListAdapter ⇒ _, _ }

import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._
import org.macroid.{ Tweak, ActivityContext, AppContext }
import org.macroid.contrib.Layouts.HorizontalLinearLayout

import net.routestory.R
import net.routestory.model.StoryPreview
import net.routestory.ui.RouteStoryFragment
import org.macroid.viewable.FillableViewableAdapter

class StoriesListFragment extends ListFragment
  with RouteStoryFragment
  with StoriesObserverFragment {

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

  implicit lazy val viewable = new PreviewRow(displaySize(1))
  var adapter: Option[FillableViewableAdapter[StoryPreview]] = None

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = super.onCreateView(inflater, container, savedInstanceState) ~> Bg.res(R.drawable.caspa)
    val list = view.find[ListView](android.R.id.list) ~> (tweak ~ (_.setDivider(null)))
    if (showControls) {
      list.get.addFooterView {
        l[HorizontalLinearLayout](
          w[Button] ~> text("Prev") ~> wire(prevButton) ~> On.click(storyteller.prev()),
          w[Button] ~> text("Next") ~> wire(nextButton) ~> On.click(storyteller.next())
        ) ~> Tweak[HorizontalLinearLayout](_.setGravity(Gravity.CENTER_HORIZONTAL))
      }
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

  def update(data: Future[List[StoryPreview]]) = async {
    if (showReload || Option(getListAdapter).exists(_.isEmpty)) await(ui(setListShown(false)))
    Log.d("StoryList", s"Received future $data from $observer")
    val s = await(data mapUi {
      x ⇒ setEmptyText(emptyText); x
    } recoverUi {
      case t if !t.isInstanceOf[UninitializedError] ⇒ t.printStackTrace(); setEmptyText(errorText); Nil
    })
    ui {
      adapter map { a ⇒
        a.clear()
      } getOrElse {
        adapter = Some(new FillableViewableAdapter[StoryPreview] {
          override def isEnabled(position: Int) = false
        })
        setListAdapter(adapter.get)
      }
      adapter.map(_.addAll(s))
      setListShown(true)
    }
    prevButton ~> enable(storyteller.hasPrev)
    nextButton ~> enable(storyteller.hasNext)
  }
}
