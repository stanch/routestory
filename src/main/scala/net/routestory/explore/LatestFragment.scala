package net.routestory.explore

import net.routestory.parts.{ WidgetFragment, StoryFragment }
import android.view.{ View, ViewGroup, LayoutInflater }
import android.os.Bundle
import android.widget.{ TextView, LinearLayout }
import net.routestory.R
import org.scaloid.common._
import scala.concurrent.Promise
import akka.dataflow._
import org.ektorp.ViewQuery
import net.routestory.model._
import scala.concurrent.ExecutionContext.Implicits.global
import android.graphics.Point

class LatestFragment(number: Int) extends StoryFragment with WidgetFragment {
  lazy val list = findView[LinearLayout](Id.list)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    new VerticalLinearLayout(ctx) {
      this += new TextView(ctx) {
        setText(R.string.explore_lateststories)
        setTextAppearance(ctx, R.style.ExploreSectionAppearance)
      }
      this += new VerticalLinearLayout(ctx) {
        setId(Id.list)
      }
    }
  }

  override def onStart() {
    super.onStart()

    val displaySize = new Point
    getActivity.getWindowManager.getDefaultDisplay.getSize(displaySize)

    loaded.tryCompleteWith(flow {
      val query = new ViewQuery().designDocId("_design/Story").viewName("byTimestamp").descending(true).limit(number)
      val (stories, _, _) = await(app.getQueryResults[StoryResult](remote = true, query, None))
      val authors = await(app.getObjects[Author](stories.filter(_.authorId != null).map(_.authorId)))
      stories.filter(_.authorId != null).foreach(s ⇒ s.author = authors(s.authorId))
      switchToUiThread()
      list.removeAllViews()
      stories foreach { s ⇒
        list.addView(ResultRow.getView(null, displaySize.x, s, getActivity))
      }
    })
  }
}
