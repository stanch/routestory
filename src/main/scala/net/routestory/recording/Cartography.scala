package net.routestory.recording

import scala.concurrent.ExecutionContext.Implicits.global

import android.os.Bundle
import android.view.{ LayoutInflater, ViewGroup }

import akka.actor.{ Actor, ActorSystem, Props }
import com.google.android.gms.maps.{ CameraUpdateFactory, SupportMapFragment }
import com.google.android.gms.maps.model.{ CameraPosition, LatLng, Marker }
import org.macroid._
import org.macroid.FullDsl._

import net.routestory.display.RouteMapManager
import net.routestory.model.Story
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.FragmentData

class CartographyFragment extends RouteStoryFragment with FragmentData[ActorSystem] with IdGeneration {
  lazy val cartographer = getFragmentData.actorSelection("/user/cartographer")
  lazy val map = findFrag[SupportMapFragment](Tag.map).get.getMap

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    f[SupportMapFragment].framed(Id.map, Tag.map)
  }

  override def onStart() {
    super.onStart()
    cartographer ! Cartographer.AttachUi(this)
  }

  override def onStop() {
    super.onStop()
    cartographer ! Cartographer.DetachUi
  }
}

object Cartographer {
  case class AttachUi(fragment: CartographyFragment)
  case object DetachUi
  case class Location(coords: LatLng, bearing: Float)
  case class Update(chapter: Story.Chapter)
  case object QueryLastLocation
  def props(implicit ctx: ActivityContext, appCtx: AppContext) = Props(new Cartographer)
}

/** An actor that updates the map with the chapter data, as well as current location */
class Cartographer(implicit ctx: ActivityContext, appCtx: AppContext) extends Actor {
  import Cartographer._

  var attachedUi: Option[CartographyFragment] = None
  def map = attachedUi.map(_.map)
  var mapManager: Option[RouteMapManager] = None
  var manMarker: Option[Marker] = None
  var last: Option[LatLng] = None

  lazy val typewriter = context.actorSelection("../typewriter")

  def receive = {
    case AttachUi(fragment) ⇒ ui {
      attachedUi = Some(fragment)
      mapManager = Some(new RouteMapManager(fragment.map, fragment.displaySize)(fragment.displaySize.min / 8))
    }

    case DetachUi ⇒
      attachedUi = None
      mapManager = None
      manMarker = None

    case Update(chapter) ⇒ ui {
      mapManager.foreach(_.add(chapter))
    }

    case Location(coords, bearing) ⇒ ui {
      // update the map
      last = Some(coords)
      mapManager.foreach(_.updateMan(coords))
      map.foreach(m ⇒ m.animateCamera(CameraUpdateFactory.newCameraPosition {
        CameraPosition.builder(m.getCameraPosition).target(coords).tilt(45).zoom(19).bearing(bearing).build()
      }))
      typewriter ! Typewriter.Location(coords)
    }

    case QueryLastLocation ⇒ sender ! last
  }
}