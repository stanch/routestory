package net.routestory.recording

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Promise, Future, future }

import com.google.android.gms.maps.{ SupportMapFragment, CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model._

import android.location.{ Location ⇒ AndroidLocation }
import android.os.Bundle
import android.view._
import android.widget.{ ProgressBar, LinearLayout }
import net.routestory.R
import net.routestory.display.DisplayActivity
import net.routestory.display.RouteMapManager
import net.routestory.model.Story
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
import akka.actor.{ Props, Actor, ActorSystem }
import org.macroid.{ Toasts, UiThreading }
import android.content.Context
import net.routestory.parts.Implicits._
import android.app.Activity
import net.routestory.external.Foursquare
import play.api.libs.json.Json
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import net.routestory.model.JsonFormats._

object RecordActivity {
  val REQUEST_CODE_TAKE_PICTURE = 0
  val REQUEST_CONNECTION_FAILURE_RESOLUTION = 1
}

trait LocationHandler
  extends GooglePlayServicesClient.ConnectionCallbacks
  with GooglePlayServicesClient.OnConnectionFailedListener
  with LocationListener { self: RouteStoryActivity ⇒

  // a request code to use for connection failure resolution
  val rqGmsConnectionFailureResolution: Int

  lazy val locationClient = new LocationClient(this, this, this)
  val firstLocationPromise = Promise[Unit]()

  def trackLocation() {
    Option(locationClient).filter(!_.isConnected).foreach(_.connect())
  }
  def looseLocation() {
    Option(locationClient).filter(_.isConnected).foreach { c ⇒ c.removeLocationUpdates(this); c.disconnect() }
  }

  def onConnected(bundle: Bundle) {
    val request = LocationRequest.create()
      .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
      .setInterval(5000) // 5 seconds
      .setFastestInterval(5000) // 5 seconds
    locationClient.requestLocationUpdates(request, this)
  }
  def onDisconnected() {}
  def onConnectionFailed(connectionResult: ConnectionResult) {
    if (connectionResult.hasResolution) {
      Try {
        connectionResult.startResolutionForResult(this, rqGmsConnectionFailureResolution)
      } recover { case t ⇒ t.printStackTrace() }
    } else {
      // TODO: show error
      toast(connectionResult.getErrorCode.toString) ~> fry
    }
  }
}

class RecordActivity extends RouteStoryActivity with LocationHandler {
  val rqGmsConnectionFailureResolution = RecordActivity.REQUEST_CONNECTION_FAILURE_RESOLUTION
  lazy val map = findFrag[SupportMapFragment](Tag.recordingMap).get.getMap
  var progress = slot[ProgressBar]

  lazy val actorSystem = ActorSystem("RecordingActorSystem", app.config, app.getClassLoader)
  implicit lazy val uiActor = actorSystem.actorOf(Props.empty, "ui")
  lazy val cartographer = actorSystem.actorOf(Cartographer.props(map, displaySize, WeakReference(this)), "cartographer")
  lazy val typewriter = actorSystem.actorOf(Typewriter.props, "typewriter")

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setContentView {
      l[VerticalLinearLayout](
        activityProgress ~>
          wire(progress) ~>
          showProgress(firstLocationPromise.future),
        w[HapticButton] ~> text("Add stuff") ~> TextSize.large ~>
          layoutParams(MATCH_PARENT, WRAP_CONTENT) ~>
          On.click(new AddMediaDialogFragment().show(getSupportFragmentManager, Tag.addMedia)),
        l[LinearLayout](
          f[SupportMapFragment].framed(Id.map, Tag.recordingMap)
        )
      )
    }

    // setup action bar
    bar.setDisplayShowTitleEnabled(true)
    bar.setDisplayShowHomeEnabled(true)

    // restore the chapter
    Option(savedInstanceState).filter(_.containsKey("savedChapter")).map(_.getString("savedChapter")).flatMap { json ⇒
      Json.parse(json).asOpt[Story.Chapter]
    } map { chapter ⇒
      typewriter ! Typewriter.Restore(chapter)
    }
  }

  override def onStart() {
    super.onStart()
    trackLocation()
  }

  override def onStop() {
    super.onStop()
    looseLocation()
  }

  def onLocationChanged(location: AndroidLocation) {
    firstLocationPromise.trySuccess(())
    // TODO: send it to only one of them
    cartographer ! Cartographer.Location(location, location.getBearing)
    typewriter ! Typewriter.Location(location)
  }

  override def onSaveInstanceState(outState: Bundle) = {
    implicit val timeout = Timeout(5 seconds)
    val chapter = Await.result((typewriter ? Typewriter.Backup).mapTo[Story.Chapter], 5 seconds)
    val json = Json.toJson(chapter).toString
    outState.putString("savedChapter", json)
    super.onSaveInstanceState(outState)
  }
}

/* This actor updates the map */
object Cartographer {
  case class Location(coords: LatLng, bearing: Float)
  case class Update(chapter: Story.Chapter)
  case object QueryLast
  def props(map: GoogleMap, displaySize: List[Int], activity: WeakReference[Activity])(implicit ctx: Context) =
    Props(new Cartographer(map, displaySize, activity))
}
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

/* This actor maintains the chapter being recorded */
object Typewriter {
  case class Location(coords: LatLng)
  case class Photo(url: String)
  case class TextNote(text: String)
  case class Heartbeat(bpm: Int)
  case object Backup
  case class Restore(chapter: Story.Chapter)
  def props(implicit ctx: Context) = Props(new Typewriter())
}
class Typewriter(implicit ctx: Context) extends Actor with Toasts {
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

    case Foursquare.Venue(id, name, lat, lng) ⇒
      addMedia(Story.Venue(ts, id, name, new LatLng(lat, lng)))

    case Location(coords) ⇒
      chapter = chapter.copy(locations = Story.Location(ts, coords) :: chapter.locations)

    case Restore(ch) ⇒
      chapter = ch
  }

  def receive = addingStuff.andThen(_ ⇒ cartographer ! Cartographer.Update(chapter)) orElse {
    case Backup ⇒
      sender ! chapter
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
