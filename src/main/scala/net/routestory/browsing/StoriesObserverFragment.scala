package net.routestory.browsing

import scala.concurrent.Future

import android.support.v4.app.Fragment

import rx.{ Obs, Rx }

import net.routestory.model.StoryPreview
import net.routestory.util.FragmentData

trait HazStories {
  val stories: Rx[Future[List[StoryPreview]]]
  def next() {}
  def prev() {}
  def hasNext = false
  def hasPrev = false
}

trait StoriesObserverFragment extends FragmentData[HazStories] { self: Fragment ⇒
  lazy val storyteller = getFragmentData
  lazy val stories = storyteller.stories
  var observer: Option[Obs] = None

  def update(data: Future[List[StoryPreview]])

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