package net.routestory.ui

import android.graphics.Color
import android.support.v7.widget.CardView
import android.view.ViewGroup.LayoutParams._
import android.widget._
import android.view.{ ViewGroup, View }
import net.routestory.R
import macroid.{ Tweak, AppContext }
import macroid.FullDsl._
import macroid.contrib.ExtraTweaks._

object Styles {
  def p8dding(implicit ctx: AppContext) = padding(all = 8 dp)

  val wrapContent = lp[ViewGroup](WRAP_CONTENT, WRAP_CONTENT)
  val matchParent = lp[ViewGroup](MATCH_PARENT, MATCH_PARENT)

  val noDivider = Tweak[ListView] { x ⇒
    x.setDividerHeight(0)
    x.setDivider(null)
  }

  val card = Tweak[CardView](_.setRadius(4))

  def seek(p: Int) = Tweak[SeekBar](_.setProgress(p))

  def adaptr(a: ListAdapter) = Tweak[ListView](_.setAdapter(a))

  def header(noPadding: Boolean = false)(implicit ctx: AppContext) =
    TextStyle.bold + TextSize.sp(25) + Tweak[TextView] { x ⇒
      x.setTextColor(ctx.get.getResources.getColor(R.color.orange))
    } + (if (!noPadding) padding(top = 15 sp) else Tweak.blank)

  def title(implicit ctx: AppContext) =
    TextSize.sp(30) + TextStyle.bold + Tweak[TextView](_.setTextColor(ctx.get.getResources.getColor(R.color.aquadark)))

  val medium = TextSize.sp(18)

  def tag(implicit ctx: AppContext) = medium + TextFace.serif + TextStyle.italic + padding(right = 8 dp)

  val lowProfile = Tweak[View](_.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE))

  val immerse = Tweak[View](_.setSystemUiVisibility {
    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  })

  val unImmerse = Tweak[View](_.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE))
}
