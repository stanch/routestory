package net.routestory.recording

import akka.actor.{ Actor, Props }
import com.google.android.gms.maps.model.LatLng
import org.macroid.AppContext
import org.needs.foursquare
import net.routestory.model.Story

object Typewriter {
  case class Location(coords: LatLng)
  case class Photo(url: String)
  case class TextNote(text: String)
  case class Heartbeat(bpm: Int)
  case object Backup
  case class Restore(chapter: Story.Chapter)
  case object StartOver
  def props(implicit ctx: AppContext) = Props(new Typewriter())
}

/** An actor that maintains the chapter being recorded */
class Typewriter(implicit ctx: AppContext) extends Actor {
  import Typewriter._

  def cartographer = context.actorSelection("../cartographer")
  def ts = (System.currentTimeMillis / 1000L - chapter.start).toInt
  var chapter = Story.Chapter(System.currentTimeMillis / 1000L, 0, Nil, Nil)
  def addMedia(m: Story.Media) = chapter = chapter.copy(media = m :: chapter.media)

  val addingStuff: Receive = {
    case Photo(url) ⇒
      addMedia(Story.Photo(ts, url))

    case TextNote(text) ⇒
      addMedia(Story.TextNote(ts, text))

    case Heartbeat(bpm) ⇒
      addMedia(Story.Heartbeat(ts, bpm))

    case foursquare.Venue(id, name, lat, lng) ⇒
      addMedia(Story.Venue(ts, id, name, new LatLng(lat, lng)))

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