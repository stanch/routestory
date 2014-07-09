package net.routestory.browsing

import akka.actor.{ ActorLogging, Props }
import android.os.Bundle
import android.view.{ LayoutInflater, View, ViewGroup }
import android.widget.FrameLayout
import com.etsy.android.grid.StaggeredGridView
import com.google.android.gms.maps.model.{ f ⇒ _ }
import macroid.FullDsl._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.viewable.FillableViewableAdapter
import macroid.{ IdGeneration, Tweak }
import net.routestory.data.{ Clustering, Story }
import net.routestory.ui._
import net.routestory.viewable.StoryElementViewable

class TimelineFragment extends RouteStoryFragment with AkkaFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/timeliner"))

  var grid = slot[StaggeredGridView]

  def viewTree(chapter: Story.Chapter, scale: Double, tree: Clustering.Tree, path: Clustering.TreePath) = {
    val viewables = new StoryElementViewable(chapter, 200 dp)
    val nodes = tree.childrenAtScale(scale, path)
    val elements = nodes flatMap { n ⇒ n._1.elements }
    val adapter = FillableViewableAdapter(elements)(viewables)
    grid <~ Tweak[StaggeredGridView] { x ⇒
      x.setAdapter(adapter)
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[FrameLayout](
      w[StaggeredGridView] <~ wire(grid) <~ Styles.matchParent <~ Tweak[StaggeredGridView] { x ⇒
        val field = x.getClass.getDeclaredField("mItemMargin")
        field.setAccessible(true)
        field.set(x, 8 dp)
      }
    )
  }
}

object Timeliner {
  case object Prev
  case object Next
  def props = Props(new Timeliner)
}

class Timeliner extends FragmentActor[TimelineFragment] with ActorLogging {
  import net.routestory.browsing.Coordinator._

  lazy val coordinator = context.actorSelection("../coordinator")

  def receive = receiveUi andThen {
    case FragmentActor.AttachUi(_) ⇒
      coordinator ! Remind

    case UpdateTree(c, tree) ⇒
      log.debug("Updating")
      tree foreach { t ⇒
        withUi(_.viewTree(c, 100, t, Nil))
      }

    case _ ⇒
  }
}
