package net.routestory.recording

import akka.actor.{ Actor, Props }
import akka.pattern.pipe
import com.javadocmd.simplelatlng.LatLng
import macroid.AppContext
import macroid.Loafs._
import macroid.ToastDsl._
import macroid.UiThreading._
import net.routestory.data.{ Clustering, Story, Timed }
import net.routestory.util.Implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Typewriter {
  case class Element(element: Story.KnownElement)
  case class Location(location: LatLng)
  case object Remind
  case class Cluster(tree: Option[Clustering.Tree[Unit]])
  def props(implicit ctx: AppContext) = Props(new Typewriter())
}

/** An actor that maintains the chapter being recorded */
class Typewriter(implicit ctx: AppContext) extends Actor {
  import net.routestory.recording.Typewriter._

  def cartographer = context.actorSelection("../cartographer")
  var chapter = Story.Chapter.empty
  var tree: Option[Clustering.Tree[Unit]] = None

  def receive = {
    case Element(element) ⇒
      chapter = chapter.withElement(Timed(chapter.ts, element))
      //Future(Clustering.cluster(chapter)).map(Cluster).pipeTo(self)
      runUi {
        toast(s"Added $element") <~ fry
      }

    case Location(location) ⇒
      chapter = chapter.withLocation(Timed(chapter.ts, location))
      cartographer ! Cartographer.Update(chapter, tree)

    case Cluster(t) ⇒
      tree = t
      cartographer ! Cartographer.Update(chapter, tree)

    case Remind ⇒
      cartographer ! Cartographer.Update(chapter, tree)
  }
}
