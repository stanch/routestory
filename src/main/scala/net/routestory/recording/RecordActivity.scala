package net.routestory.recording

import scala.async.Async._
import scala.concurrent.{ Await, Promise }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

import android.app.AlertDialog
import android.content.Intent
import android.location.{ Location ⇒ AndroidLocation }
import android.os.Bundle
import android.view.{ KeyEvent, Menu, MenuItem }
import android.view.ViewGroup.LayoutParams._
import android.widget.{ Button, LinearLayout, ProgressBar }

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.pattern.ask
import akka.util.Timeout
import com.google.android.gms.common._
import com.google.android.gms.location.{ LocationClient, LocationListener, LocationRequest }
import org.macroid.FullDsl._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import play.api.libs.json.{ JsValue, Json, JsObject }
import play.api.data.mapping.{ From, To }

import net.routestory.R
import net.routestory.model.Story
import net.routestory.needs.SavingFormats._
import net.routestory.recording.manual.AddMediaFragment
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import net.routestory.util.{ FragmentDataProvider, FragmentData, Shortuuid }
import net.routestory.util.Implicits._
import org.macroid.{ Tweak, IdGeneration }
import net.routestory.recording.suggest.{ Suggester, SuggestionsFragment }
import net.routestory.recording.logged.{ Dictaphone, ControlPanelFragment }
import android.support.v4.view.ViewPager
import org.needs.Resolvable
import net.routestory.browsing.StoryActivity

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

class RecordActivity extends RouteStoryActivity with LocationHandler with IdGeneration with FragmentPaging with FragmentDataProvider[ActorSystem] {
  val rqGmsConnectionFailureResolution = RecordActivity.REQUEST_CONNECTION_FAILURE_RESOLUTION
  var progress = slot[ProgressBar]
  lazy val pager = this.find[ViewPager](Id.pager)

  lazy val actorSystem = ActorSystem("RecordingActorSystem", app.config, app.getClassLoader)
  implicit lazy val uiActor = actorSystem.actorOf(Props.empty, "ui")
  lazy val cartographer: ActorRef = actorSystem.actorOf(Cartographer.props, "cartographer")
  lazy val typewriter: ActorRef = actorSystem.actorOf(Typewriter.props, "typewriter")
  lazy val suggester: ActorRef = actorSystem.actorOf(Suggester.props(app), "suggester")
  lazy val dictaphone: ActorRef = actorSystem.actorOf(Dictaphone.props, "dictaphone")

  def getFragmentData(tag: String) = actorSystem

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setContentView {
      l[VerticalLinearLayout](
        activityProgress ~>
          wire(progress) ~>
          showProgress(firstLocationPromise.future),
        getTabs(
          "Add stuff" → f[AddMediaFragment].factory,
          "Map" → f[CartographyFragment].factory,
          "Suggestions" → f[SuggestionsFragment].factory,
          "Control panel" → f[ControlPanelFragment].factory
        )
      )
    }

    // setup action bar
    bar.setDisplayShowTitleEnabled(true)
    bar.setDisplayShowHomeEnabled(true)

    // restore the chapter or create a new one
    Option(savedInstanceState).filter(_.containsKey("savedChapter")).map(_.getString("savedChapter")).flatMap { json ⇒
      From[JsValue, Resolvable[Story.Chapter]](Json.parse(json))(app.api.chapterRule).asOpt
    } map { chapter ⇒
      chapter.go.foreach(c ⇒ typewriter ! Typewriter.Restore(c))
    } getOrElse {
      typewriter ! Typewriter.StartOver
    }
  }

  override def onStart() {
    super.onStart()
    pager ~> Tweak[ViewPager](_.setCurrentItem(1))
    (cartographer, typewriter, suggester, dictaphone) // start actors
    trackLocation()
  }

  override def onStop() {
    super.onStop()
    looseLocation()
  }

  def onLocationChanged(location: AndroidLocation) {
    firstLocationPromise.trySuccess(())
    cartographer ! Cartographer.Location(location, location.getBearing)
  }

  override def onSaveInstanceState(outState: Bundle) = {
    implicit val timeout = Timeout(5 seconds)
    val chapter = Await.result((typewriter ? Typewriter.Backup).mapTo[Story.Chapter], 5 seconds)
    val json = To[Story.Chapter, JsObject](chapter).toString()
    outState.putString("savedChapter", json)
    super.onSaveInstanceState(outState)
  }

  def giveUp() {
    finish()
  }

  def createNew() {
    progress ~@> waitProgress(async {
      implicit val timeout = Timeout(5 seconds)
      val id = Shortuuid.make("story")
      val chapter = await((typewriter ? Typewriter.Backup).mapTo[Story.Chapter])
      val story = Story(id, Story.Meta(None, None), List(chapter), None)
      app.createStory(story)
      val intent = new Intent(this, classOf[StoryActivity])
      intent.putExtra("id", id)
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      ui(startActivity(intent))
    } recover {
      case t ⇒ t.printStackTrace(); throw t
    })
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.addStuff ⇒
      pager ~> Tweak[ViewPager](_.setCurrentItem(0))
      true
    case R.id.controlPanel ⇒
      pager ~> Tweak[ViewPager](_.setCurrentItem(3))
      true
    case R.id.stopRecord ⇒
      new AlertDialog.Builder(this) {
        setMessage(R.string.message_stoprecord)
        setPositiveButton(android.R.string.yes, createNew())
        setNegativeButton(android.R.string.no, ())
        create()
      }.show()
      true
    case _ ⇒ super.onOptionsItemSelected(item)
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent) = keyCode match {
    case KeyEvent.KEYCODE_BACK ⇒ giveUp(); false
    case _ ⇒ super.onKeyDown(keyCode, event)
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.activity_record, menu)
    true
  }
}

