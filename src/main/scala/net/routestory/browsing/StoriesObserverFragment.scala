package net.routestory.browsing

import android.support.v4.app.Fragment
import net.routestory.data.StoryPreview
import rx.{ Obs, Rx }

import scala.concurrent.Future

trait HazStories {
  val stories: Rx[Future[List[StoryPreview]]]
  def next() {}
  def prev() {}
  def hasNext = false
  def hasPrev = false
}

trait StoriesObserverFragment { self: Fragment ⇒
  lazy val storyteller: HazStories = null
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