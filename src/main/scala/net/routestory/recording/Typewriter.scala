package net.routestory.recording

import akka.actor.{ ActorLogging, Actor, Props }
import akka.pattern.pipe
import com.javadocmd.simplelatlng.LatLng
import macroid.{ Ui, AppContext }
import macroid.Loafs._
import macroid.ToastDsl._
import macroid.UiThreading._
import net.routestory.Apis
import net.routestory.data.{ Clustering, Story, Timed }
import net.routestory.util.Implicits._
import net.routestory.util.Shortuuid

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Promise, Future }

object Typewriter {
  case class Element(element: Story.KnownElement)
  case class Location(location: LatLng)
  case object Remind
  case class Discard(done: Promise[Unit])
  case class Save(storyId: String, done: Promise[Unit])
  case class Cluster(tree: Option[Clustering.Tree[Unit]])
  def props(service: RecordService, apis: Apis)(implicit ctx: AppContext) = Props(new Typewriter(service, apis))
}

/** An actor that maintains the chapter being recorded */
class Typewriter(service: RecordService, apis: Apis)(implicit ctx: AppContext) extends Actor with ActorLogging {
  import net.routestory.recording.Typewriter._

  def cartographer = context.actorSelection("../cartographer")
  var chapter = Story.Chapter.empty
  var tree: Option[Clustering.Tree[Unit]] = None

  def receive = {
    case Element(element) ⇒
      chapter = chapter.withElement(Timed(chapter.ts, element))
      Future(Clustering.cluster(chapter)).map(Cluster).pipeTo(self)
      runUi {
        toast(s"Added $element") <~ fry
      }

    case Location(location) ⇒
      chapter = chapter.withLocation(Timed(chapter.ts, location))
      cartographer ! Cartographer.UpdateRoute(chapter)

    case Cluster(t) ⇒
      tree = t
      cartographer ! Cartographer.UpdateMarkers(chapter, tree)

    case Remind ⇒
      cartographer ! Cartographer.UpdateRoute(chapter)
      cartographer ! Cartographer.UpdateMarkers(chapter, tree)

    case Discard(done) ⇒
      done.success(())
      Ui(service.stopSelf()).run

    case Save(id, done) ⇒
      // TODO: add to existing story
      val story = Story.empty(id).withChapter(chapter.finish)
      apis.hybridApi.saveStory(story)
      done.success(())
      Ui(service.stopSelf()).run
  }
}
