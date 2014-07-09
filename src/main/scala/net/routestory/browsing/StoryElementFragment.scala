package net.routestory.browsing

import akka.actor.{ ActorLogging, Props }
import android.graphics.Color
import android.os.Bundle
import android.support.v4.view.{ ViewPager, PagerAdapter }
import android.view.{ View, ViewGroup, LayoutInflater }
import android.widget.FrameLayout
import macroid.contrib.ExtraTweaks.Bg
import macroid.util.Ui
import macroid.{ Tweak, AppContext, ActivityContext }
import macroid.FullDsl._
import macroid.akkafragments.{ FragmentActor, AkkaFragment }
import net.routestory.data.Story
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.{ActiveObject, FragmentOf, ProxiedFragment, CoFragment}
import net.routestory.viewable.{ StoryElementDetailedViewable, StoryElementViewable }

class ElementPagerAdapter(chapter: Story.Chapter)(implicit ctx: ActivityContext, appCtx: AppContext) extends PagerAdapter {
  val viewables = new StoryElementDetailedViewable(300 dp)

  override def instantiateItem(container: ViewGroup, position: Int) = {
    val view = getUi(viewables.layout(chapter.knownElements(position)))
    container.addView(view, 0)
    view
  }

  override def destroyItem(container: ViewGroup, position: Int, `object`: Any) = {
    container.removeView(`object`.asInstanceOf[View])
  }

  def getCount = chapter.knownElements.length

  def isViewFromObject(view: View, `object`: Any) = view == `object`
}

class StoryElementFragment extends RouteStoryFragment with ProxiedFragment[StoryElementFragment] with FragmentOf[StoryActivity] {
  val coFragment = activity.viewer
  var pager = slot[ViewPager]

  def viewElements(chapter: Story.Chapter) = {
    val adapter = new ElementPagerAdapter(chapter)
    pager <~ Tweak[ViewPager] { x ⇒
      x.setAdapter(adapter)
    }
  }

  def cue(cue: Int) = pager <~ Tweak[ViewPager] { x ⇒
    x.setCurrentItem(cue)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    w[ViewPager] <~ wire(pager) <~ Bg.color(Color.BLACK)
  }
}

class Viewer2(coordinator: ActiveObject) extends CoFragment[StoryElementFragment] {


  override def attachUi(f: StoryElementFragment) = {
    super.attachUi(f)
    coordinator
  }

  def updateChapter(c: Story.Chapter) = active {
    withUi(_.viewElements(c))
  }

  def updateCue(c: Story.Chapter, cue: Int) = active {
    withUi(_.cue(cue))
  }

}

object Viewer {
  case object Prev
  case object Next
  def props = Props(new Viewer)
}

class Viewer extends FragmentActor[StoryElementFragment] with ActorLogging {
  import net.routestory.browsing.Coordinator._

  lazy val coordinator = context.actorSelection("../coordinator")

  def receive = receiveUi andThen {
    case FragmentActor.AttachUi(_) ⇒
      coordinator ! Remind

    case UpdateChapter(c) ⇒
      withUi(_.viewElements(c))

    case UpdateCue(c, cue) ⇒
      withUi(_.cue(cue))

    case _ ⇒
  }
}
