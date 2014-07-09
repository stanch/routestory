package net.routestory.recording

import net.routestory.data.Story
import net.routestory.maps.RouteMapManager

import scala.concurrent.ExecutionContext.Implicits.global

import android.os.Bundle
import android.view.{ LayoutInflater, ViewGroup }

import akka.actor.{ ActorLogging, Actor, ActorSystem, Props }
import com.google.android.gms.maps.{ CameraUpdateFactory, SupportMapFragment }
import com.google.android.gms.maps.model.{ CameraPosition, LatLng, Marker }
import macroid._
import macroid.FullDsl._

import net.routestory.ui.RouteStoryFragment
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.util.Ui

class CartographyFragment extends RouteStoryFragment with AkkaFragment with IdGeneration {
  lazy val actor = Some(actorSystem.actorSelection("/user/cartographer"))
  lazy val map = this.findFrag[SupportMapFragment](Tag.map).get.get.getMap

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    f[SupportMapFragment].framed(Id.map, Tag.map)
  }
}

object Cartographer {
  case class Location(coords: LatLng, bearing: Float)
  case class Update(chapter: Story.Chapter)
  case object QueryLastLocation
  def props(implicit ctx: ActivityContext, appCtx: AppContext) = Props(new Cartographer)
}

/** An actor that updates the map with the chapter data, as well as current location */
class Cartographer(implicit ctx: ActivityContext, appCtx: AppContext) extends FragmentActor[CartographyFragment] with ActorLogging {
  import Cartographer._
  import FragmentActor._

  var mapManager: Option[RouteMapManager] = None
  var last: Option[LatLng] = None

  lazy val typewriter = context.actorSelection("../typewriter")

  def receive = receiveUi andThen {
    case AttachUi(_) ⇒ withUi(f ⇒ Ui {
      mapManager = Some(new RouteMapManager(f.map, f.displaySize)(f.displaySize.min / 8))
    })

    case DetachUi ⇒ withUi(f ⇒ Ui {
      mapManager.foreach(_.remove())
      mapManager = None
    })

    case Update(chapter) ⇒ withUi(f ⇒ Ui {
      mapManager.foreach(_.add(chapter))
    })

    case Location(coords, bearing) ⇒ withUi(f ⇒ Ui {
      // update the map
      log.debug("Received location")
      last = Some(coords)
      mapManager.foreach(_.updateMan(coords))
      f.map.animateCamera(CameraUpdateFactory.newCameraPosition {
        CameraPosition.builder(f.map.getCameraPosition).target(coords).tilt(45).zoom(19).bearing(bearing).build()
      })
      typewriter ! Typewriter.Location(coords)
    })

    case QueryLastLocation ⇒ sender ! last
  }
}