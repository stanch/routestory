package net.routestory.explore

import net.routestory.parts.{ WidgetFragment, StoryFragment }
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

class LatestFragment extends ResultListFragment with HazStories {
  lazy val latestStories = Var(fetchStories)
  def getStories = latestStories
  override lazy val storyteller = this
  override val showControls = false

  lazy val number = Option(getArguments).map(_.getInt("number")).getOrElse(3)

  def fetchStories = async {
    val query = new ViewQuery().designDocId("_design/Story").viewName("byTimestamp").descending(true).limit(number)
    val (stories, _, _) = await(app.getQueryResults[StoryResult](remote = true, query, None))
    val authors = await(app.getObjects[Author](stories.filter(_.authorId != null).map(_.authorId)))
    stories.filter(_.authorId != null).foreach(s ⇒ s.author = authors(s.authorId))
    stories
  } onFailureUi {
    case t ⇒ t.printStackTrace()
  }
}

object LatestFragment {
  def newInstance(number: Int) = {
    val frag = new LatestFragment
    val bundle = new Bundle
    bundle.putInt("number", number)
    frag.setArguments(bundle)
    frag
  }
}
