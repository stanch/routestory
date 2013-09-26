package net.routestory.explore

import net.routestory.parts.{ WidgetFragment, StoryFragment }
import android.view.{ View, ViewGroup, LayoutInflater }
import android.os.{ Looper, Bundle }
import android.widget.{ TextView, LinearLayout }
import net.routestory.R
import org.scaloid.common._
import org.ektorp.ViewQuery
import net.routestory.model._
import scala.concurrent.ExecutionContext.Implicits.global
import android.graphics.Point
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import scala.async.Async.{ async, await }

class LatestFragment extends StoryFragment with WidgetFragment {
  lazy val number = Option(getArguments).map(_.getInt("number")).getOrElse(3)
  var list: LinearLayout = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[VerticalLinearLayout](
      w[TextView] ~> text(R.string.explore_lateststories) ~> headerStyle(),
      l[VerticalLinearLayout]() ~> wire(list)
    )
  }

  override def onStart() {
    super.onStart()

    val displaySize = new Point
    getActivity.getWindowManager.getDefaultDisplay.getSize(displaySize)

    loaded.tryCompleteWith(async {
      val query = new ViewQuery().designDocId("_design/Story").viewName("byTimestamp").descending(true).limit(number)
      val (stories, _, _) = await(app.getQueryResults[StoryResult](remote = true, query, None))
      val authors = await(app.getObjects[Author](stories.filter(_.authorId != null).map(_.authorId)))
      stories.filter(_.authorId != null).foreach(s ⇒ s.author = authors(s.authorId))
      list ~> addViews(stories.map(ResultRow.getView(None, displaySize.x, _, getActivity)), removeOld = true)
    })
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
