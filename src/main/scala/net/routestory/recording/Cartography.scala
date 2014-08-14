package net.routestory.recording

import akka.actor.{ ActorLogging, Props }
import android.app.Activity
import android.location.Location
import android.os.Bundle
import android.view.{ LayoutInflater, ViewGroup }
import com.google.android.gms.maps.model.{ CameraPosition, LatLng }
import com.google.android.gms.maps.{ CameraUpdateFactory, SupportMapFragment }
import macroid.FullDsl._
import macroid._
import macroid.akkafragments.FragmentActor
import net.routestory.browsing.story.MapManager
import net.routestory.data.{ Clustering, Story }
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.Implicits._

import scala.concurrent.ExecutionContext.Implicits.global

class CartographyFragment extends RouteStoryFragment with IdGeneration with RecordFragment with AutoLogTag {
  lazy val actor = actorSystem.map(_.actorSelection("/user/cartographer"))
  lazy val map = this.findFrag[SupportMapFragment](Tag.map).get.get.getMap
  lazy val mapManager = new MapManager(map, iconAlpha = 0.7f, centerIcons = false)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    f[SupportMapFragment].framed(Id.map, Tag.map)
  }

  override def onStart() = {
    super.onStart()
    actor.foreach(_ ! FragmentActor.AttachUi(this))
  }

  override def onStop() = {
    super.onStop()
    actor.foreach(_ ! FragmentActor.DetachUi(this))
  }

  def positionMap(location: Location) = Ui {
    map.animateCamera(CameraUpdateFactory.newCameraPosition {
      CameraPosition.builder(map.getCameraPosition)
        .target(location: LatLng)
        .tilt(45).zoom(19).bearing(location.getBearing).build()
    })
  }
}

object Cartographer {
  case class Locate(location: Location)
  case class UpdateRoute(chapter: Story.Chapter)
  case class UpdateMarkers(chapter: Story.Chapter, tree: Option[Clustering.Tree[Unit]])
  case object QueryLastLocation
  def props = Props(new Cartographer)
}

/** An actor that updates the map with the chapter data, as well as current location */
class Cartographer extends FragmentActor[CartographyFragment] with ActorLogging {
  import macroid.akkafragments.FragmentActor._
  import net.routestory.recording.Cartographer._

  var last: Option[Location] = None

  lazy val typewriter = context.actorSelection("../typewriter")

  def receive = receiveUi andThen {
    case AttachUi(_) ⇒
      typewriter ! Typewriter.Remind
      withUi { f ⇒
        last.fold(Ui.nop)(f.positionMap)
      }

    case UpdateRoute(chapter) ⇒
      withUi(_.mapManager.addRoute(chapter))

    case UpdateMarkers(chapter, tree) ⇒
      withUi(_.mapManager.addMarkers(chapter, tree))

    case Locate(location) ⇒
      withUi { f ⇒
        f.positionMap(location) ~
          f.mapManager.addMan(location) ~
          Ui(last = Some(location)) ~
          Ui(typewriter ! Typewriter.Location(location))
      }

    case QueryLastLocation ⇒
      sender ! last

    case _ ⇒
  }
}