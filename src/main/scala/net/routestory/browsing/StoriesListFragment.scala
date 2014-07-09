package net.routestory.browsing

import net.routestory.data.StoryPreview

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
import net.routestory.ui.{ Styles, RouteStoryFragment }
import macroid.viewable.FillableViewableAdapter
import net.routestory.viewable.StoryPreviewViewable
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

  implicit lazy val viewable = StoryPreviewViewable
  var adapter: Option[FillableViewableAdapter[StoryPreview]] = None

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    val view = super.onCreateView(inflater, container, savedInstanceState)
    val list = view.find[ListView](android.R.id.list) <~ Styles.noDivider
    val withFooter = if (showControls) {
      val footer = l[HorizontalLinearLayout](
        w[Button] <~ text("Prev") <~ wire(prevButton) <~ On.click(Ui(storyteller.prev())) <~ enable(storyteller.hasPrev),
        w[Button] <~ text("Next") <~ wire(nextButton) <~ On.click(Ui(storyteller.next())) <~ enable(storyteller.hasNext)
      ) <~ Tweak[HorizontalLinearLayout](_.setGravity(Gravity.CENTER_HORIZONTAL))
      footer.flatMap(f ⇒ list <~ Tweak[ListView](_.addFooterView(f)))
    } else list
    withFooter ~ Ui(view)
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
    runUi(
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
    )
  }
}
