package net.routestory.ui

import macroid.FullDsl._
import macroid.{ ActivityContext, AppContext }
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.viewable.{ FillableViewable, Viewable }
import macroid.viewable.Viewing._
import android.widget.{ TextView, LinearLayout }
import android.view.View
import macroid.Ui

class CollapsedListView[A](header: Ui[View])(implicit ctx: ActivityContext, appCtx: AppContext, dataViewable: FillableViewable[A])
  extends VerticalLinearLayout(ctx.get) {

  var data = slot[LinearLayout]
  var extra = slot[LinearLayout]

  runUi {
    this <~ addViews(Seq(
      header <~ hide,
      w[VerticalLinearLayout]() <~ wire(data),
      w[VerticalLinearLayout]() <~ wire(extra)
    ))
  }

  def setData(items: List[A]) = Ui.sequence(
    header <~ show(!data.isEmpty),
    data <~ addViews(items.take(3).map(_.layout), removeOld = true),
    extra <~ addViews(items.drop(3).map(_.layout), removeOld = true)
  )
}

