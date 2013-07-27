package net.routestory.parts

import org.macroid.LayoutDsl._
import android.widget.TextView
import android.content.Context
import android.view.View
import org.scaloid.common._
import net.routestory.R

object Transforms {
  def mediumText[A <: TextView](implicit ctx: Context): ViewMutator[A] =
    x ⇒ x.setTextAppearance(ctx, android.R.style.TextAppearance_Medium)

  def largeText[A <: TextView](implicit ctx: Context): ViewMutator[A] =
    x ⇒ x.setTextAppearance(ctx, android.R.style.TextAppearance_Large)

  def headerText[A <: TextView](implicit ctx: Context): ViewMutator[A] =
    x ⇒ x.setTextAppearance(ctx, R.style.ExploreSectionAppearance)

  def p8dding[A <: View](implicit ctx: Context): ViewMutator[A] =
    x ⇒ x.setPadding(8 dip, 8 dip, 8 dip, 8 dip)
}
