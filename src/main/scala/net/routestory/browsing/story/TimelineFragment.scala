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
import net.routestory.data.{ Timed, Clustering, Story }
import net.routestory.ui._
import net.routestory.viewable.{ CardListable, TimedListable, StoryElementListable }
import macroid.viewable._

class TimelineFragment extends RouteStoryFragment with AkkaFragment with StaggeredFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/timeliner"))
  lazy val coordinator = actorSystem.actorSelection("/user/coordinator")

  def viewChapter(chapter: Story.Chapter) = {
    val listable = CardListable.cardListable(
      new TimedListable(chapter).timedListable(
        StoryElementListable.storyElementListable))

    grid <~ listable.listAdapterTweak(chapter.knownElements) <~
      FuncOn.itemClick[StaggeredGridView] { (_: AdapterView[_], _: View, index: Int, _: Long) ⇒
        ElementPager.show(
          Clustering.Leaf(chapter.knownElements(index), index, chapter, ()),
          f ⇒ coordinator ! Coordinator.UpdateFocus(chapter, f)
        )
      }
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
      withUi(_.viewChapter(c))

    case _ ⇒
  }
}
