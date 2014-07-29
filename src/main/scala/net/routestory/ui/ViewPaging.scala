package net.routestory.ui

import android.content.Context
import android.graphics.Color
import android.support.v4.app.{ Fragment, FragmentManager, FragmentPagerAdapter }
import android.support.v4.view.ViewPager
import android.view.View
import com.viewpagerindicator.TitlePageIndicator
import macroid.FullDsl._
import macroid._
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.contrib.{ PagerTweaks, BgTweaks, LpTweaks }
import net.routestory.R

class MapAwareViewPager(ctx: Context) extends ViewPager(ctx) {
  override def canScroll(scrollingView: View, checkV: Boolean, dx: Int, x: Int, y: Int) = {
    implicit val c = AppContext(ctx)
    val cls = for {
      v ← Option(scrollingView)
      cl ← Option(v.getClass)
      p ← Option(cl.getPackage)
      n ← Option(p.getName)
    } yield n
    if (cls.exists(_.startsWith("maps."))) {
      x > 20.dp && x < scrollingView.getWidth - 20.dp
    } else {
      super.canScroll(scrollingView, checkV, dx, x, y)
    }
  }
}

case class PagingAdapter(fm: FragmentManager, frags: Seq[(CharSequence, Ui[Fragment])]) extends FragmentPagerAdapter(fm) {
  def getCount = frags.length
  def getItem(position: Int) = frags(position)._2.get
  override def getPageTitle(position: Int): CharSequence = frags(position)._1
}

trait FragmentPaging { self: IdGeneration ⇒
  def getTabs(frags: (CharSequence, Ui[Fragment])*)(implicit ctx: ActivityContext, manager: FragmentManagerContext[Fragment, FragmentManager]) = {
    val pager = w[MapAwareViewPager] <~
      id(Id.pager) <~
      PagerTweaks.adapter(PagingAdapter(manager.get, frags))
    pager.flatMap { p ⇒
      val indicator = w[TitlePageIndicator] <~
        LpTweaks.matchWidth <~
        BgTweaks.color(Color.BLACK) <~ Tweak[TitlePageIndicator] { x ⇒
          x.setViewPager(p)
          x.setFooterColor(ctx.get.getResources.getColor(R.color.aquadark))
        }
      l[VerticalLinearLayout](indicator, Ui(p))
    }
  }
}