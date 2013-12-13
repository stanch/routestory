package net.routestory.recording

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Promise, Future, future }

import com.google.android.gms.maps.{ SupportMapFragment, CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model._

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
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
import net.routestory.parts.Implicits._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import scala.async.Async.{ async, await }
import com.google.android.gms.common._
import scala.util.{ Success, Try }
import com.google.android.gms.location.{ LocationRequest, LocationClient, LocationListener }
import akka.actor.{ Actor, ActorSystem }
import org.macroid.UiThreading

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

  def trackLocation() {
    locationClient.connect()
  }
  def looseLocation() {
    Option(locationClient).filter(_.isConnected).foreach { c ⇒ c.removeLocationUpdates(this); c.disconnect() }
  }

  def onConnected(bundle: Bundle) {
    toast("Connected!") ~> fry
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
  lazy val actorSystem = ActorSystem("RecordingActorSystem")

  val firstLocationPromise = Promise[Unit]()
  var progress = slot[ProgressBar]

  lazy val map = findFrag[SupportMapFragment](Tag.recordingMap).get.getMap

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

  override def onStop() {
    actorSystem.shutdown()
  }

  def onLocationChanged(location: Location) {

  }
}

object Cartographer {
  case class Position(coords: LatLng)
  case class Update(chapter: Story.Chapter)
}
class Cartographer(map: GoogleMap) extends Actor with UiThreading {
  import Cartographer._
  lazy val routeManager = new RouteManager(map)
  var manMarker: Option[Marker] = None
  def receive = {
    case Update(chapter) ⇒ ui {
      routeManager.add(chapter)
    }
    case Position(coords) ⇒ ui {
      // move map, etc
    }
  }
}

object Typewriter {
  case class Piece(media: Story.Media)
  case object Backup
}
class Typewriter(firstSketch: Story.Chapter) extends Actor {
  import Typewriter._
  var chapter = firstSketch
  def receive = {
    case Piece(m @ Story.TextNote(ts, text)) ⇒
      chapter = chapter.copy(media = m :: chapter.media)
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
