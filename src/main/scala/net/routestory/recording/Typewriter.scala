package net.routestory.recording

import akka.actor.{ Actor, Props }
import com.google.android.gms.maps.model.LatLng
import macroid.AppContext
import macroid.Loafs._
import macroid.ToastDsl._
import macroid.UiThreading._
import net.routestory.data.{ Clustering, Story, Timed }
import net.routestory.util.Implicits._

object Typewriter {
  case object Backup
  case class Restore(chapter: Story.Chapter)
  case object StartOver
  def props(implicit ctx: AppContext) = Props(new Typewriter())
}

/** An actor that maintains the chapter being recorded */
class Typewriter(implicit ctx: AppContext) extends Actor {
  import net.routestory.recording.Typewriter._

  def cartographer = context.actorSelection("../cartographer")
  var chapter = Story.Chapter.empty
  var tree: Option[Clustering.Tree[Unit]] = None

  val addingStuff: Receive = {
    case element: Story.Element ⇒
      chapter = chapter.withElement(Timed(chapter.ts, element))
      tree = Clustering.cluster(chapter)
      runUi {
        toast(s"Added $element") <~ fry
      }

    case location: LatLng ⇒
      chapter = chapter.withLocation(Timed(chapter.ts, location))
      tree = Clustering.cluster(chapter)

    case Restore(ch) ⇒
      chapter = ch
      tree = Clustering.cluster(chapter)

    case StartOver ⇒
      chapter = Story.Chapter.empty
      tree = None
  }

  def receive = addingStuff.andThen(_ ⇒ cartographer ! Cartographer.Update(chapter, tree)) orElse {
    case Backup ⇒
      sender ! chapter
  }
}
