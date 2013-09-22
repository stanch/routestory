package net.routestory.explore

import android.view.{ View, ViewGroup, LayoutInflater }
import android.os.Bundle
import android.widget.{ TextView, LinearLayout }
import org.scaloid.common._
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.R
import net.routestory.parts.{ WidgetFragment, StoryFragment }
import akka.dataflow._
import org.ektorp.ViewQuery
import scala.util.Random
import android.graphics.Point
import scala.collection.JavaConversions._
import net.routestory.parts.Styles._
import org.macroid.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }

class TagsFragment extends StoryFragment with WidgetFragment {
  var rows: LinearLayout = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[VerticalLinearLayout](
      w[TextView] ~> text(R.string.explore_tags) ~> headerStyle(),
      l[HorizontalLinearLayout](
        l[VerticalLinearLayout]() ~> wire(rows)
      )
    )
  }

  override def onStart() {
    super.onStart()

    val displaySize = new Point
    getActivity.getWindowManager.getDefaultDisplay.getSize(displaySize)

    loaded.tryCompleteWith(flow {
      val query = new ViewQuery().designDocId("_design/Story").viewName("tags").group(true)
      val tags = await(app.getPlainQueryResults(remote = true, query))
      switchToUiThread()
      val tagArray = Random.shuffle(tags.getRows.toList).take(10).map(_.getKey)
      ResultRow.fillTags(rows, displaySize.x - 30, tagArray.toArray, getActivity)
    })
  }
}
