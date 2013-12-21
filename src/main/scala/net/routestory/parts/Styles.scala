package net.routestory.parts

import android.widget.{ ProgressBar, TextView }
import android.content.Context
import android.view.{ ViewGroup, View }
import net.routestory.R
import ViewGroup.LayoutParams._
import org.macroid.MediaQueries

object Styles extends org.macroid.contrib.ExtraTweaks with MediaQueries {
  def p8dding(implicit ctx: Context) = padding(all = 8 dp)

  def measure: Tweak[View] = _.measure(0, 0)

  def header(noPadding: Boolean = false)(implicit ctx: Context): Tweak[TextView] =
    TextStyle.bold + TextSize.sp(25) + { x ⇒
      x.setTextColor(ctx.getResources.getColor(R.color.orange))
      if (!noPadding) x ~> padding(top = 15 sp)
    }

  def storyShift(implicit ctx: Context) = 10.dp

  def title(implicit ctx: Context): Tweak[TextView] =
    TextSize.large + TextStyle.bold + Bg.res(R.color.aquadark) +
      padding(left = storyShift, top = 3 sp, bottom = 3 sp, right = 5 sp)

  def tag(implicit ctx: Context) = TextSize.medium + TextFace.serif + TextStyle.italic

  def caption(implicit ctx: Context) = TextSize.medium + TextStyle.italic + padding(left = storyShift, right = 4 sp)
}
