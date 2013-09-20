package net.routestory.parts

import org.macroid.LayoutDsl._
import android.widget.{ ImageButton, TextView }
import android.content.Context
import android.view.View
import org.scaloid.common._
import net.routestory.R

object Tweaks extends org.macroid.contrib.ExtraTweaks {
  def p8dding(implicit ctx: Context) = padding(all = (8 dip))

  def bg(resourceId: Int)(implicit ctx: Context): Tweak[View] =
    _.setBackgroundResource(resourceId)

  def measure: Tweak[View] = _.measure(0, 0)

  def headerStyle(implicit ctx: Context): Tweak[TextView] = TextStyle.bold + TextSize.sp(25) + { x ⇒
    x.setTextColor(R.color.orange)
  }

  def titleStyle(implicit ctx: Context): Tweak[TextView] =
    TextSize.large + TextStyle.bold + padding(all = (3 sp)) + (_.setBackgroundResource(R.color.aquadark))

  def tagStyle(implicit ctx: Context) = TextSize.medium + TextFace.serif + TextStyle.italic

  def captionStyle(implicit ctx: Context) = TextSize.medium + TextStyle.italic + padding(right = (4 sp))
}
