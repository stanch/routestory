package net.routestory.ui

import android.graphics.Color
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.CardView
import android.view.ViewGroup.LayoutParams._
import android.widget._
import android.view.{ ViewGroup, View }
import com.etsy.android.grid.StaggeredGridView
import macroid.contrib.TextTweaks
import net.routestory.R
import macroid.{ Tweak, AppContext }
import macroid.FullDsl._

object Styles {
  def p8dding(implicit ctx: AppContext) = padding(all = 8 dp)

  val card = Tweak[CardView](_.setRadius(4))

  def grid(implicit ctx: AppContext) = Tweak[StaggeredGridView] { x ⇒
    val field = x.getClass.getDeclaredField("mItemMargin")
    field.setAccessible(true)
    field.set(x, 8 dp)
  }

  val stopRefresh = Tweak[SwipeRefreshLayout](_.setRefreshing(false))
  val startRefresh = Tweak[SwipeRefreshLayout](_.setRefreshing(true))

  def header(noPadding: Boolean = false)(implicit ctx: AppContext) =
    TextTweaks.bold + TextTweaks.size(25) +
      TextTweaks.color(ctx.get.getResources.getColor(R.color.orange)) +
      (if (!noPadding) padding(top = 15 sp) else Tweak.blank)

  def title(implicit ctx: AppContext) =
    TextTweaks.size(30) + TextTweaks.bold +
      TextTweaks.color(ctx.get.getResources.getColor(R.color.aquadark))

  def tag(implicit ctx: AppContext) =
    TextTweaks.medium + TextTweaks.serif +
      TextTweaks.italic + padding(right = 8 dp)

  val lowProfile = Tweak[View](_.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE))

  val immerse = Tweak[View](_.setSystemUiVisibility {
    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  })

  val unImmerse = Tweak[View](_.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE))
}
