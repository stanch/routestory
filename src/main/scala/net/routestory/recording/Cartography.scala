package net.routestory.recording

import akka.actor.{ ActorLogging, Props }
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
import akka.pattern.pipe
import scala.concurrent.duration._

import scala.concurrent.Promise

class CartographyFragment extends RouteStoryFragment with IdGeneration with RecordFragment with AutoLogTag {
  import scala.concurrent.ExecutionContext.Implicits.global

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
  case object QueryFirstLocation
  case object Notify
  def props(implicit ctx: AppContext) = Props(new Cartographer)
}

/** An actor that updates the map with the chapter data, as well as current location */
class Cartographer(implicit ctx: AppContext) extends FragmentActor[CartographyFragment] with ActorLogging {
  import macroid.akkafragments.FragmentActor._
  import net.routestory.recording.Cartographer._
  import context.dispatcher

  var last: Option[Location] = None
  val first = Promise[Unit]()

  lazy val typewriter = context.actorSelection("../typewriter")

  var notifications = Option(context.system.scheduler
    .schedule(3 seconds, 10 seconds, self, Notify))
  var notified = false
  first.future.foreach { _ ⇒
    notifications.foreach(_.cancel())
    notifications = None
  }

  def receive = receiveUi andThen {
    case AttachUi(_) ⇒
      typewriter ! Typewriter.Remind
      withUi { f ⇒
        last.fold(Ui.nop)(f.positionMap)
      }

    case Notify ⇒
      val msg = if (!notified) "Searching for location..." else "Still searching for location..."
      notified = true
      runUi {
        toast(msg) <~ fry
      }

    case UpdateRoute(chapter) ⇒
      withUi(_.mapManager.addRoute(chapter))

    case UpdateMarkers(chapter, tree) ⇒
      withUi { f ⇒
        f.mapManager.removeMarkers() ~
          f.mapManager.addMarkers(chapter, tree)
      }

    case Locate(location) if location.getAccuracy < 20 ⇒
      last = Some(location)
      first.trySuccess(())
      typewriter ! Typewriter.Location(location)
      withUi { f ⇒
        f.positionMap(location) ~
          f.mapManager.addMan(location)
      }

    case QueryLastLocation ⇒
      sender ! last

    case QueryFirstLocation ⇒
      first.future.pipeTo(sender)

    case _ ⇒
  }
}