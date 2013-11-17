package net.routestory.explore

import android.support.v4.app.Fragment
import net.routestory.lounge2.Puffy
import net.routestory.model2.StoryPreview
import net.routestory.parts.FragmentData
import rx.{ Obs, Rx }
import scala.concurrent.Future

trait HazStories {
  type Stories = List[Puffy[StoryPreview]]

  val stories: Rx[Future[Stories]]
  def next() {}
  def prev() {}
  def hasNext = false
  def hasPrev = false
}

trait StoriesObserverFragment extends FragmentData[HazStories] { self: Fragment ⇒
  lazy val storyteller = getFragmentData
  lazy val stories = storyteller.stories
  var observer: Option[Obs] = None

  def update(data: Future[HazStories#Stories])

  def observe() {
    if (observer.isEmpty) {
      // create a new observer
      observer = Some(Obs(stories)(update(stories())))
    }
    // start observing
    observer.foreach(_.active = true)
  }

  def neglect() {
    // stop observing, since we go out of screen
    observer.foreach(_.active = false)
  }
}