package net.routestory.recording

import scala.concurrent.ExecutionContext.Implicits.global
import scala.ref.WeakReference

import android.app.Activity
import android.content.Context

import akka.actor.{ Actor, Props }
import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model.{ CameraPosition, LatLng, Marker }
import org.macroid.UiThreading

import net.routestory.display.RouteMapManager
import net.routestory.model.Story

object Cartographer {
  case class Location(coords: LatLng, bearing: Float)
  case class Update(chapter: Story.Chapter)
  case object QueryLast
  def props(map: GoogleMap, displaySize: List[Int], activity: WeakReference[Activity])(implicit ctx: Context) =
    Props(new Cartographer(map, displaySize, activity))
}

/** An actor that updates the map with the chapter data, as well as current location */
class Cartographer(map: GoogleMap, displaySize: List[Int], activity: WeakReference[Activity])(implicit ctx: Context)
  extends Actor with UiThreading {
  import Cartographer._

  lazy val mapManager = new RouteMapManager(map, displaySize, activity)(displaySize.min / 8)
  var manMarker: Option[Marker] = None
  var last: Option[LatLng] = None

  def receive = {
    case Update(chapter) ⇒ ui {
      mapManager.add(chapter)
    }

    case Location(coords, bearing) ⇒ ui {
      // update the map
      last = Some(coords)
      mapManager.updateMan(coords)
      map.animateCamera(CameraUpdateFactory.newCameraPosition {
        CameraPosition.builder(map.getCameraPosition).target(coords).tilt(45).zoom(19).bearing(bearing).build()
      })
    }

    case QueryLast ⇒ sender ! last
  }
}