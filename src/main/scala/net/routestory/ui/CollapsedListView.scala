package net.routestory.ui

import org.macroid.FullDsl._
import org.macroid.{ ActivityContext, AppContext }
import org.macroid.contrib.Layouts.VerticalLinearLayout
import org.macroid.viewable.{ FillableViewable, Viewable }
import org.macroid.viewable.Viewing._
import android.widget.{ TextView, LinearLayout }
import android.view.View

class CollapsedListView[A](header: View)(implicit ctx: ActivityContext, appCtx: AppContext, dataViewable: FillableViewable[A])
  extends VerticalLinearLayout(ctx.get) {

  var data = slot[LinearLayout]
  var extra = slot[LinearLayout]

  this ~> addViews(Seq(
    header ~> hide,
    w[VerticalLinearLayout]() ~> wire(data),
    w[VerticalLinearLayout]() ~> wire(extra)
  ))

  def setData(items: List[A]) = {
    header ~> show(!data.isEmpty)
    data ~> addViews(items.take(3).map(_.layout), removeOld = true)
    extra ~> addViews(items.drop(3).map(_.layout), removeOld = true)
  }
}

