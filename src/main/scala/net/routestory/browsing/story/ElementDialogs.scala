package net.routestory.browsing.story

import android.graphics.Color
import android.support.v4.view.ViewPager.OnPageChangeListener
import android.support.v4.view.{ PagerAdapter, ViewPager }
import android.view.{ View, ViewGroup }
import android.widget.AdapterView
import com.etsy.android.grid.StaggeredGridView
import macroid.FullDsl._
import macroid.contrib.{ BgTweaks, ListTweaks }
import macroid.viewable.FillableViewableAdapter
import macroid.{ ActivityContext, AppContext, Tweak }
import net.routestory.data.{ Clustering, Story }
import net.routestory.ui.Styles
import net.routestory.viewable.{ StoryElementViewable, StoryElementDetailedViewable }

class ElementPagerAdapter(chapter: Story.Chapter)(implicit ctx: ActivityContext, appCtx: AppContext) extends PagerAdapter {
  val viewables = new StoryElementDetailedViewable(300 dp)

  override def instantiateItem(container: ViewGroup, position: Int) = {
    val view = getUi(viewables.layout(chapter.knownElements(position).data))
    container.addView(view, 0)
    view
  }

  override def destroyItem(container: ViewGroup, position: Int, `object`: Any) = {
    container.removeView(`object`.asInstanceOf[View])
  }

  def getCount = chapter.knownElements.length

  def isViewFromObject(view: View, `object`: Any) = view == `object`
}

object ElementPager {
  def show[A](leaf: Clustering.Leaf[A], onCue: Int ⇒ Unit)(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val adapter = new ElementPagerAdapter(leaf.chapter)
    dialog(android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
      w[ViewPager] <~ BgTweaks.color(Color.BLACK) <~ Styles.lowProfile <~ Tweak[ViewPager] { x ⇒
        x.setAdapter(adapter)
        x.setCurrentItem(leaf.index)
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
    val viewables = new StoryElementViewable(200 dp)
    val adapter = FillableViewableAdapter(elements)(viewables)
    dialog {
      w[StaggeredGridView] <~ Styles.grid <~ ListTweaks.adapter(adapter) <~
        FuncOn.itemClick[StaggeredGridView] { (_: AdapterView[_], _: View, index: Int, _: Long) ⇒
          ElementPager.show(node.leaves(index), onCue)
        }
    } <~ speak
  }
}
