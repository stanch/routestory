package net.routestory.browsing.story

import akka.actor.{ ActorLogging, Props }
import android.graphics.Color
import android.os.Bundle
import android.support.v4.view.ViewPager.OnPageChangeListener
import android.support.v4.view.{ PagerAdapter, ViewPager }
import android.view.{ LayoutInflater, View, ViewGroup }
import macroid.FullDsl._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.contrib.BgTweaks
import macroid.{ ActivityContext, AppContext, Tweak }
import net.routestory.data.Clustering
import net.routestory.ui.RouteStoryFragment
import net.routestory.viewable.ClusteringTreeViewable

class PreviewPagerAdapter(trees: Vector[Clustering.Tree[Unit]])(implicit ctx: ActivityContext, appCtx: AppContext) extends PagerAdapter {
  def viewables = new ClusteringTreeViewable[Unit](200 dp)

  override def instantiateItem(container: ViewGroup, position: Int) = {
    val view = getUi(viewables.layout(trees(position)))
    container.addView(view, 0)
    view
  }

  override def destroyItem(container: ViewGroup, position: Int, `object`: Any) = {
    container.removeView(`object`.asInstanceOf[View])
  }

  def getCount = trees.length

  def isViewFromObject(view: View, `object`: Any) = view == `object`
}

class PreviewFragment extends RouteStoryFragment with AkkaFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/previewer"))
  lazy val coordinator = actorSystem.actorSelection("/user/coordinator")

  var pager = slot[ViewPager]

  def viewTrees(trees: Vector[Clustering.Tree[Unit]]) = {
    val adapter = new PreviewPagerAdapter(trees)
    pager <~ Tweak[ViewPager] { x ⇒
      x.setAdapter(adapter)
      x.setOnPageChangeListener(new OnPageChangeListener {
        override def onPageScrollStateChanged(state: Int) = ()
        override def onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = ()
        override def onPageSelected(position: Int) = {
          val leaf = trees(position).leaves.head
          coordinator ! Coordinator.UpdateFocus(leaf.chapter, leaf.index)
        }
      })
    }
  }

  def focus(focus: Int) = pager <~ Tweak[ViewPager](_.setCurrentItem(focus))

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    w[ViewPager] <~ BgTweaks.color(Color.BLACK) <~ wire(pager)
  }
}

object Previewer {
  case class UpdateScale(scale: Double)

  def props = Props(new Previewer)
}

class Previewer extends FragmentActor[PreviewFragment] with ActorLogging {
  import net.routestory.browsing.story.Coordinator._
  import net.routestory.browsing.story.Previewer._

  lazy val coordinator = context.actorSelection("../coordinator")

  var tree: Option[Clustering.Tree[Unit]] = None
  var trees: Vector[Clustering.Tree[Unit]] = Vector.empty

  def receive = receiveUi andThen {
    case FragmentActor.AttachUi(_) ⇒
      coordinator ! Remind

    case UpdateTree(c, t) ⇒
      tree = t

    case UpdateScale(s) ⇒
      trees = tree.fold(Vector.empty[Clustering.Tree[Unit]])(_.childrenAtScale(s))
      withUi(_.viewTrees(trees))

    case UpdateFocus(c, focus) ⇒
      Clustering.indexLookup(trees, focus) foreach { i ⇒
        withUi(f ⇒ f.focus(i))
      }

    case _ ⇒
  }
}
