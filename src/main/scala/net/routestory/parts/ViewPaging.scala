package net.routestory.parts

import android.support.v4.view.ViewPager
import android.content.Context
import android.view.{ ViewGroup, View }
import android.support.v4.app.{ FragmentPagerAdapter, FragmentManager, Fragment }
import org.macroid._
import org.macroid.util.Thunk
import com.viewpagerindicator.TitlePageIndicator
import ViewGroup.LayoutParams._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import org.macroid.contrib.ExtraTweaks
import net.routestory.R

class MapAwareViewPager(ctx: Context) extends ViewPager(ctx) with MediaQueries {
  override def canScroll(scrollingView: View, checkV: Boolean, dx: Int, x: Int, y: Int) = {
    implicit val c = ctx
    if (scrollingView.getClass.getPackage.getName.startsWith("maps.")) {
      x > 20.dp && x < scrollingView.getWidth - 20.dp
    } else {
      super.canScroll(scrollingView, checkV, dx, x, y)
    }
  }
}

case class PagingAdapter(fm: FragmentManager, frags: Seq[(CharSequence, Thunk[Fragment])]) extends FragmentPagerAdapter(fm) {
  def getCount = frags.length
  def getItem(position: Int) = frags(position)._2()
  override def getPageTitle(position: Int): CharSequence = frags(position)._1
}

trait FragmentPaging extends LayoutDsl with ExtraTweaks { self: ViewSearch ⇒
  def getTabs(frags: (CharSequence, Thunk[Fragment])*)(implicit ctx: Context) = {
    val pager = w[MapAwareViewPager] ~>
      id(Id.pager) ~>
      (_.setAdapter(PagingAdapter(fragmentManager, frags)))
    val indicator = w[TitlePageIndicator] ~>
      lpOf[ViewGroup](MATCH_PARENT, WRAP_CONTENT) ~>
      Bg.res(R.color.dark) ~>
      (_.setViewPager(pager)) ~>
      (_.setFooterColor(ctx.getResources.getColor(R.color.aquadark)))
    l[VerticalLinearLayout](indicator, pager)
  }
}