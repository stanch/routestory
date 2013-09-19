package net.routestory.parts

import org.macroid.LayoutDsl._
import android.widget.{ ImageButton, TextView }
import android.content.Context
import android.view.View
import org.scaloid.common._
import net.routestory.R

object Tweaks {
  def mediumText(implicit ctx: Context): Tweak[TextView] =
    _.setTextAppearance(ctx, android.R.style.TextAppearance_Medium)

  def largeText(implicit ctx: Context): Tweak[TextView] =
    _.setTextAppearance(ctx, android.R.style.TextAppearance_Large)

  def headerText(implicit ctx: Context): Tweak[TextView] =
    _.setTextAppearance(ctx, R.style.ExploreSectionAppearance)

  def p8dding(implicit ctx: Context): Tweak[View] =
    _.setPadding(8 dip, 8 dip, 8 dip, 8 dip)

  def bg(resourceId: Int)(implicit ctx: Context): Tweak[View] =
    _.setBackgroundResource(resourceId)

  def measure: Tweak[View] = _.measure(0, 0)

  def titleAppearance(implicit ctx: Context): Tweak[TextView] =
    _.setTextAppearance(ctx, R.style.TitleAppearance)

  def tagAppearance(implicit ctx: Context): Tweak[TextView] =
    _.setTextAppearance(ctx, R.style.TagAppearance)
}
