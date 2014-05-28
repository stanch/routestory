package net.routestory.recording

import akka.actor.{ Actor, Props }
import com.google.android.gms.maps.model.LatLng
import macroid.AppContext
import macroid.UiThreading._
import macroid.ToastDsl._
import macroid.Loafs._
import resolvable.foursquare
import net.routestory.model.Story
import net.routestory.Apis
import java.io.File
import scala.concurrent.Future

object Typewriter {
  case class Location(coords: LatLng)
  case class Sound(file: File)
  case class Photo(file: File)
  case class TextNote(text: String)
  case class VoiceNote(file: File)
  case class Heartbeat(bpm: Int)
  case object Backup
  case class Restore(chapter: Story.Chapter)
  case object StartOver
  def props(implicit ctx: AppContext) = Props(new Typewriter())
}

/** An actor that maintains the chapter being recorded */
class Typewriter(implicit ctx: AppContext) extends Actor {
  import Typewriter._
  import context.dispatcher

  def cartographer = context.actorSelection("../cartographer")
  def ts = (System.currentTimeMillis / 1000L - chapter.start).toInt
  var chapter = Story.Chapter(System.currentTimeMillis / 1000L, 0, Nil, Nil)
  def addMedia(m: Story.Media) = chapter = chapter.copy(media = m :: chapter.media)

  val addingStuff: Receive = {
    case Photo(file) ⇒
      addMedia(Story.Photo(ts, file.getAbsolutePath, Future.successful(file)))
      runUi {
        toast("Added a photo!") <~ fry
      }

    case Sound(file) ⇒
      addMedia(Story.Sound(ts, file.getAbsolutePath, Future.successful(file)))
      runUi {
        toast("Added background sound!") <~ fry
      }

    case TextNote(text) ⇒
      addMedia(Story.TextNote(ts, text))
      runUi {
        toast("Added a text note!") <~ fry
      }

    case VoiceNote(file) ⇒
      addMedia(Story.VoiceNote(ts, file.getAbsolutePath, Future.successful(file)))
      runUi {
        toast("Added a voice note!") <~ fry
      }

    case Heartbeat(bpm) ⇒
      addMedia(Story.Heartbeat(ts, bpm))
      runUi {
        toast("Added heartbeat!") <~ fry
      }

    case foursquare.Venue(id, name, lat, lng) ⇒
      addMedia(Story.Venue(ts, id, name, new LatLng(lat, lng)))
      runUi {
        toast("Added a venue!") <~ fry
      }

    case Location(coords) ⇒
      chapter = chapter.copy(locations = Story.Location(ts, coords) :: chapter.locations)

    case Restore(ch) ⇒
      chapter = ch

    case StartOver ⇒
      chapter = Story.Chapter(System.currentTimeMillis / 1000L, 0, Nil, Nil)
  }

  def receive = addingStuff.andThen(_ ⇒ cartographer ! Cartographer.Update(chapter)) orElse {
    case Backup ⇒
      sender ! chapter
  }
}