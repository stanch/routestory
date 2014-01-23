package net.routestory.recording

import akka.actor.{ Actor, Props }
import com.google.android.gms.maps.model.LatLng
import org.macroid.AppContext
import org.macroid.Toasts._
import org.needs.foursquare
import net.routestory.model.Story

object Typewriter {
  case class Location(coords: LatLng)
  case class Sound(url: String)
  case class Photo(url: String)
  case class TextNote(text: String)
  case class VoiceNote(url: String)
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
    case Photo(url) ⇒
      addMedia(Story.Photo(ts, url))
      toast("Added a photo!") ~> fry

    case Sound(url) ⇒
      addMedia(Story.Sound(ts, url))
      toast("Added background sound!") ~> fry

    case TextNote(text) ⇒
      addMedia(Story.TextNote(ts, text))
      toast("Added a text note!") ~> fry

    case VoiceNote(url) ⇒
      addMedia(Story.VoiceNote(ts, url))
      toast("Added a voice note!") ~> fry

    case Heartbeat(bpm) ⇒
      addMedia(Story.Heartbeat(ts, bpm))
      toast("Added heartbeat!") ~> fry

    case foursquare.Venue(id, name, lat, lng) ⇒
      addMedia(Story.Venue(ts, id, name, new LatLng(lat, lng)))
      toast("Added a venue!") ~> fry

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