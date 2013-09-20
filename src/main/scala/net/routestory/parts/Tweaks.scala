package net.routestory.parts

import org.macroid.LayoutDsl._
import android.widget.{ ImageButton, TextView }
import android.content.Context
import android.view.View
import org.scaloid.common._
import net.routestory.R
import android.graphics.Typeface
import android.util.TypedValue

object Tweaks {
  def mediumText(implicit ctx: Context): Tweak[TextView] =
    _.setTextAppearance(ctx, android.R.style.TextAppearance_Medium)

  def largeText(implicit ctx: Context): Tweak[TextView] =
    _.setTextAppearance(ctx, android.R.style.TextAppearance_Large)

  def padding(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0)(implicit ctx: Context): Tweak[View] =
    _.setPadding(left, top, right, bottom)

  def p8dding(implicit ctx: Context) = padding(8 dip, 8 dip, 8 dip, 8 dip)

  def bg(resourceId: Int)(implicit ctx: Context): Tweak[View] =
    _.setBackgroundResource(resourceId)

  def measure: Tweak[View] = _.measure(0, 0)

  object TextStyle {
    def bold: Tweak[TextView] = x ⇒ x.setTypeface(x.getTypeface, Typeface.BOLD)
    def italic: Tweak[TextView] = x ⇒ x.setTypeface(x.getTypeface, Typeface.ITALIC)
    def boldItalic: Tweak[TextView] = x ⇒ x.setTypeface(x.getTypeface, Typeface.BOLD_ITALIC)
  }

  def serif: Tweak[TextView] = _.setTypeface(Typeface.SERIF)

  def headerStyle(implicit ctx: Context): Tweak[TextView] = TextStyle.bold + { x ⇒
    x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25)
    x.setTextColor(R.color.orange)
  }

  def titleStyle(implicit ctx: Context): Tweak[TextView] =
    largeText + TextStyle.bold + padding(3 sp, 3 sp, 3 sp, 3 sp) + (_.setBackgroundResource(R.color.aquadark))

  def tagStyle(implicit ctx: Context) = mediumText + serif + TextStyle.italic

  def captionStyle(implicit ctx: Context) = mediumText + padding(right = (4 sp)) + TextStyle.italic
}
