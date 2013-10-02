package net.routestory.explore

import net.routestory.parts.{ WidgetFragment, RouteStoryFragment }
import android.view.{ View, ViewGroup, LayoutInflater }
import android.os.{ Looper, Bundle }
import android.widget.{ ListView, ScrollView, TextView, LinearLayout }
import net.routestory.R
import org.scaloid.common._
import org.ektorp.ViewQuery
import net.routestory.model._
import scala.concurrent.ExecutionContext.Implicits.global
import android.graphics.Point
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import scala.async.Async.{ async, await }
import rx.{ Var, Rx }
import scala.concurrent.Future
import android.util.Log

class LatestFragment extends StoryListFragment with HazStories {
  lazy val latestStories = Var {
    Log.d("Latest", "init")
    fetchStories
  }
  def getStories = latestStories
  override val showControls = false
  override val showReloadProgress: Boolean = false

  override lazy val storyteller = this
  override lazy val emptyText = "Couldn’t load stories :("

  lazy val number = Option(getArguments).map(_.getInt("number")).getOrElse(3)

  override def onStart() {
    super.onStart()
    Log.d("Latest", "onStart")
    if (latestStories.now.isCompleted) latestStories.update(fetchStories)
  }

  def fetchStories = async {
    Log.d("Latest", "Fetching latest stories")
    val query = new ViewQuery().designDocId("_design/Story").viewName("byTimestamp").descending(true).limit(number)
    val (stories, _, _) = await(app.getQueryResults[StoryResult](remote = true, query, None))
    val authors = await(app.getObjects[Author](stories.filter(_.authorId != null).map(_.authorId)))
    stories.filter(_.authorId != null).foreach(s ⇒ s.author = authors(s.authorId))
    stories
  } recover {
    case t ⇒ t.printStackTrace(); Nil
  }
}
