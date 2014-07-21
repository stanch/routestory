package net.routestory.browsing.story

import akka.actor.{ ActorLogging, Props }
import android.os.Bundle
import android.view.{ LayoutInflater, View, ViewGroup }
import android.widget.AdapterView
import com.etsy.android.grid.StaggeredGridView
import com.google.android.gms.maps.model.{ f ⇒ _ }
import macroid.FullDsl._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.contrib.ListTweaks
import macroid.viewable.FillableViewableAdapter
import net.routestory.data.{ Clustering, Story }
import net.routestory.ui._
import net.routestory.viewable.StoryElementViewable

class TimelineFragment extends RouteStoryFragment with AkkaFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/timeliner"))
  lazy val coordinator = actorSystem.actorSelection("/user/coordinator")

  var grid = slot[StaggeredGridView]

  def addChapter(chapter: Story.Chapter) = {
    val viewables = new StoryElementViewable(chapter, displaySize(0) / 2)
    val adapter = FillableViewableAdapter(chapter.knownElements)(viewables)
    grid <~ ListTweaks.adapter(adapter) <~ FuncOn.itemClick[StaggeredGridView] { (_: AdapterView[_], _: View, index: Int, _: Long) ⇒
      ElementPager.show(Clustering.Leaf(chapter.knownElements(index), index, ())(chapter), f ⇒ coordinator ! Coordinator.UpdateFocus(chapter, f))
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    w[StaggeredGridView] <~ wire(grid) <~ Styles.grid
  }
}

object Timeliner {
  case object Prev
  case object Next
  def props = Props(new Timeliner)
}

class Timeliner extends FragmentActor[TimelineFragment] with ActorLogging {
  import net.routestory.browsing.story.Coordinator._

  lazy val coordinator = context.actorSelection("../coordinator")

  def receive = receiveUi andThen {
    case FragmentActor.AttachUi(_) ⇒
      coordinator ! Remind

    case UpdateChapter(c) ⇒
      log.debug("Updating")
      withUi(_.addChapter(c))

    case _ ⇒
  }
}
