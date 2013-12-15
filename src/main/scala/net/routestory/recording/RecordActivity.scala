package net.routestory.recording

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Promise, Future, future }

import com.google.android.gms.maps.{ SupportMapFragment, CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model._

import android.location.{ Location ⇒ AndroidLocation }
import android.os.{ Message, Handler, Bundle }
import android.view._
import android.widget.{ ProgressBar, LinearLayout }
import net.routestory.R
import net.routestory.display.DisplayActivity
import net.routestory.display.RouteManager
import net.routestory.model.Story
import net.routestory.parts.BitmapUtils
import net.routestory.parts.HapticButton
import net.routestory.parts.RouteStoryActivity
import ViewGroup.LayoutParams._
import scala.ref.WeakReference
import android.util.Log
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import com.google.android.gms.common._
import scala.util.{ Success, Try }
import com.google.android.gms.location.{ LocationRequest, LocationClient, LocationListener }
import akka.actor.{ LightArrayRevolverScheduler, Props, Actor, ActorSystem }
import org.macroid.UiThreading
import com.typesafe.config.ConfigFactory

object RecordActivity {
  val REQUEST_CODE_TAKE_PICTURE = 0
  val REQUEST_CONNECTION_FAILURE_RESOLUTION = 1
}

trait LocationHandler
  extends GooglePlayServicesClient.ConnectionCallbacks
  with GooglePlayServicesClient.OnConnectionFailedListener
  with LocationListener { self: RouteStoryActivity ⇒

  import RecordActivity._

  lazy val locationClient = new LocationClient(this, this, this)
  val firstLocationPromise = Promise[Unit]()

  def trackLocation() {
    locationClient.connect()
  }
  def looseLocation() {
    Option(locationClient).filter(_.isConnected).foreach { c ⇒ c.removeLocationUpdates(this); c.disconnect() }
  }

  def onConnected(bundle: Bundle) {
    toast("Connected!") ~> fry
    firstLocationPromise.trySuccess(())
    val request = LocationRequest.create()
      .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
      .setInterval(5000) // 5 seconds
      .setFastestInterval(5000) // 5 seconds
    locationClient.requestLocationUpdates(request, this)
  }
  def onDisconnected() {
    toast("Disconnected") ~> fry
  }
  def onConnectionFailed(connectionResult: ConnectionResult) {
    if (connectionResult.hasResolution) {
      Try {
        connectionResult.startResolutionForResult(this, REQUEST_CONNECTION_FAILURE_RESOLUTION)
      } recover { case t ⇒ t.printStackTrace() }
    } else {
      // TODO: show error
      toast(connectionResult.getErrorCode.toString) ~> fry
    }
  }
}

class RecordActivity extends RouteStoryActivity with LocationHandler {
  lazy val map = findFrag[SupportMapFragment](Tag.recordingMap).get.getMap
  var progress = slot[ProgressBar]

  lazy val actorSystem = ActorSystem("RecordingActorSystem", app.config, app.getClassLoader)
  implicit lazy val uiActor = actorSystem.actorOf(UiActor.props)
  lazy val cartographer = actorSystem.actorOf(Cartographer.props(map))

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setContentView(l[VerticalLinearLayout](
      activityProgress ~>
        wire(progress) ~>
        showProgress(firstLocationPromise.future),
      w[HapticButton] ~> text("Add stuff") ~> TextSize.large ~>
        layoutParams(MATCH_PARENT, WRAP_CONTENT) ~>
        On.click(new AddMediaDialogFragment().show(getSupportFragmentManager, Tag.addMedia)),
      l[LinearLayout](
        f[SupportMapFragment].framed(Id.map, Tag.recordingMap)
      )
    ))

    // setup action bar
    bar.setDisplayShowTitleEnabled(true)
    bar.setDisplayShowHomeEnabled(true)
  }

  override def onStart() {
    super.onStart()
    trackLocation()
  }

  override def onStop() {
    super.onStop()
    looseLocation()
    actorSystem.shutdown()
  }

  def onLocationChanged(location: AndroidLocation) {
    cartographer ! Cartographer.Location(new LatLng(location.getLatitude, location.getLongitude))
  }
}

object UiActor {
  def props = Props[UiActor]
}
class UiActor extends Actor {
  def receive = Actor.emptyBehavior
}

object Cartographer {
  case class Location(coords: LatLng)
  case class Update(chapter: Story.Chapter)
  def props(map: GoogleMap) = Props(new Cartographer(map))
}
class Cartographer(map: GoogleMap) extends Actor with UiThreading {
  import Cartographer._
  lazy val routeManager = new RouteManager(map)
  var manMarker: Option[Marker] = None
  def receive = {
    case Update(chapter) ⇒ ui {
      routeManager.add(chapter)
    }
    case Location(coords) ⇒ ui {
      // update map
      //mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder().target(pt).tilt(45).zoom(19).build())) // TODO: zoom adaptivity
      manMarker map { m ⇒
        m.setPosition(coords)
      } getOrElse {
        manMarker = Some(map.addMarker(new MarkerOptions()
          .position(coords)
          .icon(BitmapDescriptorFactory.fromResource(R.drawable.man))
        ))
      }
      map.animateCamera(CameraUpdateFactory.newLatLng(coords))
    }
  }
}

object Typewriter {
  case class Piece(media: Story.Media)
  case class Location(coords: LatLng)
  case object Backup
  def props = Props[Typewriter]
}
class Typewriter(firstSketch: Option[Story.Chapter]) extends Actor {
  import Typewriter._
  def ts = (System.currentTimeMillis / 1000L - chapter.start).toInt
  var chapter = firstSketch.getOrElse(Story.Chapter(System.currentTimeMillis / 1000L, 0, Nil, Nil))
  def receive = {
    case Piece(m @ Story.TextNote(ts, text)) ⇒
      chapter = chapter.copy(media = m :: chapter.media)
    case Location(coords) ⇒
      chapter = chapter.copy(locations = Story.Location(ts, coords) :: chapter.locations)
    case Backup ⇒
    // save chapter to disk
  }
}

object FakeAudioRecorder {
  case object Start
  case object Stop
  case object Stopped
  case object Record
}
class FakeAudioRecorder extends Actor {
  import FakeAudioRecorder._
  var enabled = false
  def receive = {
    case Start ⇒
      enabled = true
    case Stop ⇒
      enabled = false
      sender ! Stopped
    case Record if enabled ⇒
    // spawn a process to record stuff
    case _ ⇒
  }
}
