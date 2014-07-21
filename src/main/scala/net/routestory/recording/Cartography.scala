package net.routestory.recording

import akka.actor.{ ActorLogging, Props }
import android.os.Bundle
import android.view.{ LayoutInflater, ViewGroup }
import com.google.android.gms.maps.model.{ CameraPosition, LatLng }
import com.google.android.gms.maps.{ CameraUpdateFactory, SupportMapFragment }
import macroid.FullDsl._
import macroid._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.Ui
import net.routestory.browsing.story.MapManager
import net.routestory.data.Story
import net.routestory.ui.RouteStoryFragment

class CartographyFragment extends RouteStoryFragment with AkkaFragment with IdGeneration {
  lazy val actor = Some(actorSystem.actorSelection("/user/cartographer"))
  lazy val map = this.findFrag[SupportMapFragment](Tag.map).get.get.getMap
  lazy val mapManager = new MapManager(map)

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
  import net.routestory.recording.Cartographer._

  var last: Option[LatLng] = None

  lazy val typewriter = context.actorSelection("../typewriter")

  def receive = receiveUi andThen {
    case Update(chapter) ⇒
      withUi(f ⇒ f.mapManager.addRoute(chapter))

    case Location(coords, bearing) ⇒ withUi(f ⇒ Ui {
      // update the map
      log.debug("Received location")
      last = Some(coords)
      f.map.animateCamera(CameraUpdateFactory.newCameraPosition {
        CameraPosition.builder(f.map.getCameraPosition).target(coords).tilt(45).zoom(19).bearing(bearing).build()
      })
      typewriter ! Typewriter.Location(coords)
    })

    case QueryLastLocation ⇒
      sender ! last
  }
}