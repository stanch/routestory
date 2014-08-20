package net.routestory.viewable

import android.view.View
import android.widget.Button
import macroid.FullDsl._
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.viewable.Listable
import macroid.{ ActivityContext, AppContext, Ui }

class ButtonListable {
  def buttonListable[A, W <: View](listable: Listable[A, W], button: Ui[Button])(implicit ctx: ActivityContext, appCtx: AppContext) =
    Listable.wrap(listable) { w ⇒
      l[VerticalLinearLayout](button, w)
    }
}
