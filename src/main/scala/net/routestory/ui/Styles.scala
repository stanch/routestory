package net.routestory.ui

import android.widget.{ SeekBar, TextView }
import android.view.View
import net.routestory.R
import org.macroid.{ Tweak, AppContext }
import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._

object Styles {
  def p8dding(implicit ctx: AppContext) = padding(all = 8 dp)

  def measure = Tweak[View](_.measure(0, 0))

  def seek(p: Int) = Tweak[SeekBar](_.setProgress(p))

  def header(noPadding: Boolean = false)(implicit ctx: AppContext) =
    TextStyle.bold + TextSize.sp(25) + Tweak[TextView] { x ⇒
      x.setTextColor(ctx.get.getResources.getColor(R.color.orange))
      if (!noPadding) x ~> padding(top = 15 sp)
    }

  def storyShift(implicit ctx: AppContext) = 10.dp

  def title(implicit ctx: AppContext) =
    TextSize.large + TextStyle.bold + Bg.res(R.color.aquadark) +
      padding(left = storyShift, top = 3 sp, bottom = 3 sp, right = 5 sp)

  def tag(implicit ctx: AppContext) = TextSize.medium + TextFace.serif + TextStyle.italic

  def caption(implicit ctx: AppContext) = TextSize.medium + TextStyle.italic + padding(left = storyShift, right = 4 sp)

  val lowProfile = Tweak[View](_.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE))

  val immerse = Tweak[View](_.setSystemUiVisibility {
    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  })

  val unImmerse = Tweak[View](_.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE))
}
