package net.routestory.viewable

import android.support.v7.widget.CardView
import android.view.View
import macroid.viewable.Listable
import macroid.{ AppContext, ActivityContext }
import macroid.FullDsl._
import net.routestory.ui.Styles

object CardListable {
  def cardListable[A, W <: View](listable: Listable[A, W])(implicit ctx: ActivityContext, appCtx: AppContext) =
    Listable.wrap(listable) { w ⇒
      l[CardView](w) <~ Styles.card
    }
}
