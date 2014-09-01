package net.routestory.browsing.story

import akka.actor.{ ActorLogging, Props }
import android.graphics.Color
import android.os.Bundle
import android.support.v4.view.ViewPager.OnPageChangeListener
import android.support.v4.view.{ PagerAdapter, ViewPager }
import android.view.{ LayoutInflater, View, ViewGroup }
import macroid.FullDsl._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.contrib.{ PagerTweaks, BgTweaks }
import macroid.{ ActivityContext, AppContext, Tweak }
import net.routestory.data.Clustering
import net.routestory.ui.RouteStoryFragment
import net.routestory.viewable.ClusteringTreeViewable
import macroid.viewable._

class PreviewFragment extends RouteStoryFragment with AkkaFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/previewer"))
  lazy val coordinator = actorSystem.actorSelection("/user/coordinator")

  var pager = slot[ViewPager]

  def viewTrees(trees: Vector[Clustering.Tree[Unit]]) = {
    val viewable = new ClusteringTreeViewable[Unit](200 dp)
    import viewable._

    pager <~ trees.pagerAdapterTweak <~ Tweak[ViewPager] { x ⇒
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

  def focus(focus: Int) = pager <~ PagerTweaks.page(focus, smoothScroll = true)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    w[ViewPager] <~ BgTweaks.color(Color.BLACK) <~ padding(all = 4 dp) <~ wire(pager)
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
