package net.routestory.recording

import scala.concurrent.{ Await, Promise }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.ref.WeakReference
import scala.util.Try

import android.location.{ Location ⇒ AndroidLocation }
import android.os.Bundle
import android.view.ViewGroup.LayoutParams._
import android.widget.{ LinearLayout, ProgressBar }

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.pattern.ask
import akka.util.Timeout
import com.google.android.gms.common._
import com.google.android.gms.location.{ LocationClient, LocationListener, LocationRequest }
import com.google.android.gms.maps.SupportMapFragment
import org.macroid.contrib.Layouts.VerticalLinearLayout
import play.api.libs.json.Json

import net.routestory.model.Story
import net.routestory.model.JsonFormats._
import net.routestory.recording.manual.AddMedia
import net.routestory.ui.{ HapticButton, RouteStoryActivity }
import net.routestory.ui.Styles._
import net.routestory.util.Implicits._

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
  lazy val cartographer: ActorRef = actorSystem.actorOf(Cartographer.props(map, displaySize, WeakReference(this)), "cartographer")
  lazy val typewriter: ActorRef = actorSystem.actorOf(Typewriter.props, "typewriter")

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setContentView {
      l[VerticalLinearLayout](
        activityProgress ~>
          wire(progress) ~>
          showProgress(firstLocationPromise.future),
        w[HapticButton] ~> text("Add stuff") ~> TextSize.large ~>
          layoutParams(MATCH_PARENT, WRAP_CONTENT) ~>
          On.click(new AddMedia().show(getSupportFragmentManager, Tag.addMedia)),
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

