package net.routestory.parts

import android.widget.{ ProgressBar, TextView }
import android.content.Context
import android.view.{ ViewGroup, View }
import org.scaloid.common._
import net.routestory.R
import ViewGroup.LayoutParams._

object Styles extends org.macroid.contrib.ExtraTweaks {
  def p8dding(implicit ctx: Context) = padding(all = 8 dip)

  def bg(resourceId: Int)(implicit ctx: Context): Tweak[View] =
    _.setBackgroundResource(resourceId)

  def measure: Tweak[View] = _.measure(0, 0)

  def headerStyle(noPadding: Boolean = false)(implicit ctx: Context): Tweak[TextView] =
    TextStyle.bold + TextSize.sp(25) + { x ⇒
      x.setTextColor(ctx.getResources.getColor(R.color.orange))
      if (!noPadding) x ~> padding(top = 15 sp)
    }

  def storyShift(implicit ctx: Context) = (10 dip)

  def titleStyle(implicit ctx: Context): Tweak[TextView] =
    TextSize.large + TextStyle.bold + padding(left = storyShift, top = 3 sp, bottom = 3 sp, right = 5 sp) +
      (_.setBackgroundResource(R.color.aquadark))

  def tagStyle(implicit ctx: Context) = TextSize.medium + TextFace.serif + TextStyle.italic

  def captionStyle(implicit ctx: Context) = TextSize.medium + TextStyle.italic + padding(left = storyShift, right = 4 sp)
}
