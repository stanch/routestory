package net.routestory.ui

import android.content.Context
import android.graphics.Color
import android.support.v4.app.{ Fragment, FragmentManager, FragmentPagerAdapter }
import android.support.v4.view.{ PagerTabStrip, ViewPager }
import android.view.ViewGroup.LayoutParams._
import android.view.{ Gravity, View }
import android.webkit.WebView
import macroid.FullDsl._
import macroid._
import macroid.contrib.PagerTweaks
import net.routestory.R

class ScrollableViewPager(ctx: Context) extends ViewPager(ctx) {
  override def canScroll(scrollingView: View, checkV: Boolean, dx: Int, x: Int, y: Int) = {
    implicit val c = AppContext(ctx)
    val cls = for {
      v ← Option(scrollingView)
      cl ← Option(v.getClass)
      p ← Option(cl.getPackage)
      n ← Option(p.getName)
    } yield n
    if (scrollingView.isInstanceOf[WebView] || cls.exists(_.startsWith("maps."))) {
      x > 50.dp && x < scrollingView.getWidth - 50.dp
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
  def getTabs(frags: (CharSequence, Ui[Fragment])*)(implicit ctx: ActivityContext, appCtx: AppContext, manager: FragmentManagerContext[Fragment, FragmentManager]) = {
    l[ScrollableViewPager](
      w[PagerTabStrip] <~ Tweak[PagerTabStrip] { x ⇒
        x.setTabIndicatorColorResource(R.color.aquadark)
        x.setBackgroundColor(Color.BLACK)
        x.setTextColor(Color.WHITE)
        x.setPadding(4 dp, 0, 4 dp, 0)
        val lp = new ViewPager.LayoutParams {
          width = MATCH_PARENT
          height = WRAP_CONTENT
          gravity = Gravity.TOP
        }
        x.setLayoutParams(lp)
      }
    ) <~ id(Id.pager) <~ PagerTweaks.adapter(PagingAdapter(manager.get, frags))
  }
}