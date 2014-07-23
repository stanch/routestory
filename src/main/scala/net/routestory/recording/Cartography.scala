package net.routestory.recording

import akka.actor.{ ActorLogging, Props }
import android.location.Location
import android.os.Bundle
import android.view.{ LayoutInflater, ViewGroup }
import com.google.android.gms.maps.model.{ CameraPosition, LatLng }
import com.google.android.gms.maps.{ CameraUpdateFactory, SupportMapFragment }
import macroid.FullDsl._
import macroid._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import net.routestory.browsing.story.MapManager
import net.routestory.data.{ Clustering, Story }
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.Implicits._

class CartographyFragment extends RouteStoryFragment with AkkaFragment with IdGeneration {
  lazy val actor = Some(actorSystem.actorSelection("/user/cartographer"))
  lazy val map = this.findFrag[SupportMapFragment](Tag.map).get.get.getMap
  lazy val mapManager = new MapManager(map)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    f[SupportMapFragment].framed(Id.map, Tag.map)
  }
}

object Cartographer {
  case class Update(chapter: Story.Chapter, tree: Option[Clustering.Tree[Unit]])
  case object QueryLastLocation
  def props(implicit ctx: ActivityContext, appCtx: AppContext) = Props(new Cartographer)
}

/** An actor that updates the map with the chapter data, as well as current location */
class Cartographer(implicit ctx: ActivityContext, appCtx: AppContext) extends FragmentActor[CartographyFragment] with ActorLogging {
  import net.routestory.recording.Cartographer._

  var last: Option[LatLng] = None

  lazy val typewriter = context.actorSelection("../typewriter")

  def receive = receiveUi andThen {
    case Update(chapter, tree) ⇒
      withUi { f ⇒
        f.mapManager.addRoute(chapter) ~ f.mapManager.addMarkers(chapter, tree)
      }

    case location: Location ⇒ withUi(f ⇒ Ui {
      // update the map
      val latLng: LatLng = location
      log.debug("Received location")
      last = Some(latLng)
      f.map.animateCamera(CameraUpdateFactory.newCameraPosition {
        CameraPosition.builder(f.map.getCameraPosition).target(latLng).tilt(45).zoom(19).bearing(location.getBearing).build()
      })
      f.mapManager.addMan(latLng).run
      typewriter ! latLng
    })

    case QueryLastLocation ⇒
      sender ! last

    case _ ⇒
  }
}