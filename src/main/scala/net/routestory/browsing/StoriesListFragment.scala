package net.routestory.browsing

import scala.async.Async.{ async, await }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

import android.os.Bundle
import android.support.v4.app.ListFragment
import android.util.Log
import android.view.{ Gravity, LayoutInflater, View, ViewGroup }
import android.widget.{ ListAdapter ⇒ _, _ }

import macroid.FullDsl._
import macroid.contrib.ExtraTweaks._
import macroid.{ Tweak, ActivityContext, AppContext }
import macroid.contrib.Layouts.HorizontalLinearLayout

import net.routestory.R
import net.routestory.model.StoryPreview
import net.routestory.ui.RouteStoryFragment
import macroid.viewable.FillableViewableAdapter
import net.routestory.display.StoryPreviewViewable
import macroid.util.Ui

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

  implicit lazy val viewable = new StoryPreviewViewable(displaySize(1))
  var adapter: Option[FillableViewableAdapter[StoryPreview]] = None

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    val view = super.onCreateView(inflater, container, savedInstanceState) <~ Bg.res(R.drawable.caspa)
    val list = view.find[ListView](android.R.id.list) <~ Tweak[ListView](_.setDivider(null))
    if (showControls) {
      runUi(for {
        footer ← l[HorizontalLinearLayout](
          w[Button] <~ text("Prev") <~ wire(prevButton) <~ On.click(Ui(storyteller.prev())),
          w[Button] <~ text("Next") <~ wire(nextButton) <~ On.click(Ui(storyteller.next()))
        ) <~ Tweak[HorizontalLinearLayout](_.setGravity(Gravity.CENTER_HORIZONTAL))
        _ ← list <~ Tweak[ListView](_.addFooterView(footer))
        _ ← prevButton <~ enable(storyteller.hasPrev)
        _ ← nextButton <~ enable(storyteller.hasNext)
      } yield ())
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
    if (showReload || Option(getListAdapter).exists(_.isEmpty)) await(Ui(setListShown(false)).run)
    Log.d("StoryList", s"Received future $data from $observer")
    val s = await(data mapUi {
      x ⇒ setEmptyText(emptyText); x
    } recoverUi {
      case t if !t.isInstanceOf[UninitializedError] ⇒ t.printStackTrace(); setEmptyText(errorText); Nil
    })
    Ui.sequence(
      Ui {
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
      },
      prevButton <~ enable(storyteller.hasPrev),
      nextButton <~ enable(storyteller.hasNext)
    ).run
  }
}
