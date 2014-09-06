package net.routestory.browsing.story

import android.graphics.Color
import android.support.v4.view.ViewPager
import android.support.v4.view.ViewPager.OnPageChangeListener
import android.view.View
import android.widget.AdapterView
import com.etsy.android.grid.StaggeredGridView
import macroid.FullDsl._
import macroid.contrib.{ PagerTweaks, BgTweaks }
import macroid.viewable._
import macroid.{ ActivityContext, AppContext, Tweak }
import net.routestory.data.Clustering
import net.routestory.ui.{ ScrollableViewPager, Styles }
import net.routestory.viewable.{ CardListable, StoryElementListable, StoryElementViewable }

object ElementPager {
  def show[A](leaf: Clustering.Leaf[A], onCue: Int ⇒ Unit)(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val elements = leaf.chapter.knownElements.map(_.data)
    import StoryElementViewable._

    dialog(android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
      w[ScrollableViewPager] <~ BgTweaks.color(Color.BLACK) <~ Styles.lowProfile <~
        elements.pagerAdapterTweak <~ PagerTweaks.page(leaf.index) <~
        Tweak[ViewPager] { x ⇒
          x.setOnPageChangeListener(new OnPageChangeListener {
            override def onPageScrollStateChanged(state: Int) = ()
            override def onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = ()
            override def onPageSelected(position: Int) = onCue(position)
          })
        }
    } <~ speak
  }
}

object ElementChooser {
  def show[A](node: Clustering.Node[A], onCue: Int ⇒ Unit)(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val elements = node.leaves.map(_.element.data)
    val listable = CardListable.cardListable(
      StoryElementListable.storyElementListable)

    dialog {
      w[StaggeredGridView] <~ Styles.grid <~
        listable.listAdapterTweak(elements) <~
        FuncOn.itemClick[StaggeredGridView] { (_: AdapterView[_], _: View, index: Int, _: Long) ⇒
          ElementPager.show(node.leaves(index), onCue)
        }
    } <~ speak
  }
}
