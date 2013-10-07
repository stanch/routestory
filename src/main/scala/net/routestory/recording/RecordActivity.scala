package net.routestory.recording

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Promise, Future, future }

import org.scaloid.common._

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

object RecordActivity {
  val REQUEST_CODE_TAKE_PICTURE = 0
  val REQUEST_CODE_TITLE_AND_TAG = 1
  val REQUEST_CONNECTION_FAILURE_RESOLUTION = 2
}

class AudioHandler(activity: WeakReference[RecordActivity]) extends Handler {
  override def handleMessage(msg: Message) {
    val path = msg.getData.getString("path")
    val time = msg.getData.getLong("time")
    activity.get.get.audioPieces ::= (time, path)
  }
}

trait LocationHandler
  extends GooglePlayServicesClient.ConnectionCallbacks
  with GooglePlayServicesClient.OnConnectionFailedListener
  with LocationListener { self: RouteStoryActivity ⇒

  import RecordActivity._

  val locationClient: LocationClient
  def trackLocation() {
    locationClient.connect()
  }
  def looseLocation() {
    Option(locationClient).filter(_.isConnected).foreach { c ⇒ c.removeLocationUpdates(this); c.disconnect() }
  }

  def onConnected(bundle: Bundle) {
    toast("Connected!")
    val request = LocationRequest.create()
      .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
      .setInterval(5000) // 5 seconds
      .setFastestInterval(5000) // 5 seconds
    locationClient.requestLocationUpdates(request, this)
  }
  def onDisconnected() {
    toast("Disconnected")
  }
  def onConnectionFailed(connectionResult: ConnectionResult) {
    if (connectionResult.hasResolution) {
      Try {
        connectionResult.startResolutionForResult(this, REQUEST_CONNECTION_FAILURE_RESOLUTION)
      } recover { case t ⇒ t.printStackTrace() }
    } else {
      // TODO: show error
      toast(connectionResult.getErrorCode.toString)
    }
  }
}

class RecordActivity extends RouteStoryActivity with LocationHandler {
  lazy val story = new Story
  var media = Map[String, (String, String)]()
  var audioPieces = List[(Long, String)]()

  val firstLocationPromise = Promise[Unit]()
  lazy val locationClient = new LocationClient(this, this, this)

  lazy val map = findFrag[SupportMapFragment](Tag.recordingMap).get.getMap
  lazy val routeManager = new RouteManager(map, story)
  var manMarker: Option[Marker] = None

  var audioTrackerThread: Option[Thread] = None
  var toogleAudio: Boolean = false

  var progress = slot[ProgressBar]

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
        f[SupportMapFragment](Id.map, Tag.recordingMap)
      )
    ))

    // setup action bar
    bar.setDisplayShowTitleEnabled(true)
    bar.setDisplayShowHomeEnabled(true)
  }

  override def onStart() {
    super.onStart()
    trackLocation()
    //    if (started) return
    //    if (!GotoDialogFragments.ensureGPS(this)) return
    //    started = true
    //
    //    // toggle automatic audio recording
    //    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    //    mToggleAudio = prefs.getBoolean("pref_autoaudio", true)
    //
    //    // setup the map
    //    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)
    //
    //    // start tracking
    //    mProgressDialog = new ProgressDialog(ctx) {
    //      setIndeterminate(true)
    //      setMessage(getResources.getString(R.string.message_waitingforlocation))
    //      setCancelable(true)
    //      setCanceledOnTouchOutside(false)
    //      setOnCancelListener(giveUp)
    //      show()
    //    }
    //
    //    // recording starts when we receive the first location
    //    trackLocation()
  }

  override def onStop() {
    super.onStop()
    looseLocation()
  }

  //  override def onOptionsItemSelected(item: MenuItem): Boolean = {
  //    item.getItemId match {
  //      case R.id.stopRecord ⇒
  //        new AlertDialog.Builder(ctx) {
  //          setMessage(R.string.message_stoprecord)
  //          setPositiveButton(R.string.button_yes, {
  //            cleanUp(saving = true)
  //            mAudioProcessingTask = addAudio()
  //            mStory.end()
  //            val intent = SIntent[DescriptionActivity]
  //            startActivityForResult(intent, RecordActivity.REQUEST_CODE_TITLE_AND_TAG)
  //          })
  //          setNegativeButton(R.string.button_no, ())
  //          create()
  //        }.show()
  //        true
  //      case R.id.toggleAudio ⇒
  //        mToggleAudio = !mToggleAudio
  //        if (mToggleAudio) trackAudio()
  //        else untrackAudio()
  //        invalidateOptionsMenu()
  //        true
  //      case _ ⇒ super[StoryActivity].onOptionsItemSelected(item)
  //    }
  //  }

  //  def giveUp() {
  //    new AlertDialog.Builder(ctx) {
  //      setMessage(R.string.message_giveup)
  //      setPositiveButton(android.R.string.yes, {
  //        cleanUp(saving = false)
  //        finish()
  //      })
  //      setNegativeButton(android.R.string.no, {
  //        if (mRouteManager.isEmpty) {
  //          mProgressDialog.show()
  //        }
  //      })
  //      create()
  //    }.show()
  //  }

  /* just what is says on the tin */
  //  def cleanUp(saving: Boolean) {
  //    untrackLocation()
  //    untrackAudio()
  //    if (!saving) {
  //      mAudioPieces.foreach(p ⇒ new File(p._2).delete())
  //      mMedia.foreach { case (_, (path, _)) ⇒ new File(path).delete() }
  //    }
  //  }

  //  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
  //    keyCode match {
  //      case KeyEvent.KEYCODE_BACK ⇒ giveUp(); false
  //      case _ ⇒ super.onKeyDown(keyCode, event)
  //    }
  //  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.activity_record, menu)
    true
  }

  override def onPrepareOptionsMenu(menu: Menu): Boolean = {
    menu.findItem(R.id.stopRecord).setEnabled(!routeManager.isEmpty)
    menu.findItem(R.id.toggleAudio).setTitle(if (toogleAudio) R.string.menu_audioon else R.string.menu_audiooff)
    true
  }

  def onLocationChanged(location: Location) {
    toast(s"Location: $location")
    firstLocationPromise.tryComplete(Success(()))
    //    if (mRouteManager.isEmpty) mStory.start()
    //    val pt = mStory.addLocation(System.currentTimeMillis() / 1000L, location).asLatLng
    //    if (mRouteManager.isEmpty) {
    //      // now that we know where we are, start recording!
    //      firstLocationPromise.complete(Success(()))
    //      trackAudio()
    //      mRouteManager.init()
    //      invalidateOptionsMenu()
    //      mMap.addMarker(new MarkerOptions()
    //        .position(pt)
    //        .icon(BitmapDescriptorFactory.fromResource(R.drawable.flag_start))
    //        .anchor(0.3f, 1))
    //      mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder().target(pt).tilt(45).zoom(19).build())) // TODO: zoom adaptivity
    //    } else {
    //      mRouteManager.update()
    //      Option(mManMarker).map(_.remove())
    //      mManMarker = mMap.addMarker(new MarkerOptions()
    //        .position(pt)
    //        .icon(BitmapDescriptorFactory.fromResource(R.drawable.man))
    //      )
    //      mMap.animateCamera(CameraUpdateFactory.newLatLng(pt))
    //    }
  }

  /* audio tracking routines */
  def trackAudio() {
    if (toogleAudio) {
      audioTrackerThread getOrElse {
        val tracker = new AudioTracker(WeakReference(ctx), new AudioHandler(WeakReference(this)), audioPieces.length)
        audioTrackerThread = Some(new Thread(tracker, "AudioTracker"))
        audioTrackerThread.foreach(_.start())
      }
    }
  }
  def untrackAudio() = {
    audioTrackerThread map { t ⇒
      t.interrupt()
      future {
        t.join(1000)
        audioTrackerThread = None
      }
    } getOrElse {
      Future.successful(())
    }
  }

  def addAudio() {
    AudioTracker.sift(audioPieces).foreach {
      case (time, path) ⇒
        val id = story.addAudio(time, "audio/aac", "aac")
        media += id → (path, "audio/aac")
    }
    audioPieces = List()
  }

  /* adding voice */
  def addVoice(filename: String) {
    val id = story.addVoice(System.currentTimeMillis() / 1000L, "audio/m4a", "m4a")
    media += id -> (filename, "audio/m4a")
    map.addMarker(new MarkerOptions()
      .icon(BitmapDescriptorFactory.fromResource(R.drawable.voice_note))
      .position(story.getLocation(System.currentTimeMillis() / 1000L)))
  }

  /* adding text */
  def addNote(note: String) {
    story.addNote(System.currentTimeMillis() / 1000L, note)
    map.addMarker(new MarkerOptions()
      .icon(BitmapDescriptorFactory.fromResource(R.drawable.text_note))
      .position(story.getLocation(System.currentTimeMillis() / 1000L)))
  }

  /* adding venues */
  def addVenue(id: String, name: String, lat: Double, lng: Double) {
    story.addVenue(System.currentTimeMillis() / 1000L, id, name, lat, lng)
    map.addMarker(new MarkerOptions()
      .icon(BitmapDescriptorFactory.fromResource(R.drawable.foursquare))
      .position(new LatLng(lat, lng)))
  }

  /* adding heartbeat */
  def addHeartbeat(bpm: Int) {
    story.addHeartbeat(System.currentTimeMillis() / 1000L, bpm)
    map.addMarker(new MarkerOptions()
      .icon(BitmapDescriptorFactory.fromResource(R.drawable.heart))
      .position(story.getLocation(System.currentTimeMillis() / 1000L)))
  }

  /* adding photos */
  def addPhoto(filename: String) = future {
    val downsized = BitmapUtils.decodeFile(new File(filename), 1000)
    val output = new FileOutputStream(new File(filename))
    downsized.compress(Bitmap.CompressFormat.JPEG, 100, output)
    output.close()
    val id = story.addPhoto(System.currentTimeMillis() / 1000L, "image/jpg", "jpg")
    media += id -> (filename, "image/jpg")
    val bitmap = BitmapUtils.createScaledTransparentBitmap(downsized, 100, 0.8, false)
    downsized.recycle()
    Ui(map.addMarker(new MarkerOptions()
      .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
      .position(story.getLocation(System.currentTimeMillis() / 1000L)
      )))
  }

  /* adding meta */
  def addMeta(title: String, description: String, tags: String, isPrivate: Boolean) {
    story.title = title
    story.description = description
    story.setTags(tags)
    story.isPrivate = isPrivate
    story.authorId = app.authorId.now.getOrElse(null)
  }

  def createStory = async {
    await(app.createStory(story))
    var rev = story.getRevision
    val it = media.iterator
    while (it.hasNext) {
      val (id, (filename, contentType)) = it.next()
      val stream = new FileInputStream(new File(filename))
      rev = await(app.updateStoryAttachment(id, stream, contentType, story.getId, rev))
      new File(filename).delete()
    }
    await(app.compactLocal)
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    super.onActivityResult(requestCode, resultCode, data)
    requestCode match {
      case RecordActivity.REQUEST_CODE_TITLE_AND_TAG ⇒
        addMeta(
          data.getStringExtra("title"), data.getStringExtra("description"),
          data.getStringExtra("tags"), data.getBooleanExtra("private", false)
        )
        progress ~> showProgress(async {
          await(createStory)
          app.requestSync
          val intent = SIntent[DisplayActivity]
          intent.putExtra("id", story.getId)
          intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
          startActivity(intent)
        } recoverUi {
          case t ⇒
            t.printStackTrace()
            toast("Something went wrong!")
            finish()
        })
      case _ ⇒
    }
  }
}
