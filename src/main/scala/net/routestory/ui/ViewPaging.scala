package net.routestory.ui

import android.support.v4.view.ViewPager
import android.content.Context
import android.view.{ ViewGroup, View }
import android.support.v4.app.{ FragmentActivity, FragmentPagerAdapter, FragmentManager, Fragment }

import macroid.FullDsl._
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid._

import com.viewpagerindicator.TitlePageIndicator
import ViewGroup.LayoutParams._
import net.routestory.R
import macroid.util.Ui
import macroid.AppContext
import macroid.FragmentManagerContext

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
      id(Id.pager) <~ Tweak[MapAwareViewPager] { x ⇒
        x.setAdapter(PagingAdapter(manager.get, frags))
      }
    pager.flatMap { p ⇒
      val indicator = w[TitlePageIndicator] <~
        lp[ViewGroup](MATCH_PARENT, WRAP_CONTENT) <~
        Bg.res(R.color.dark) <~ Tweak[TitlePageIndicator] { x ⇒
          x.setViewPager(p)
          x.setFooterColor(ctx.get.getResources.getColor(R.color.aquadark))
        }
      l[VerticalLinearLayout](indicator, Ui(p))
    }
  }
}