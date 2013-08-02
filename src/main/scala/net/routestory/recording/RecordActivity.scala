package net.routestory.recording

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import scala.collection.JavaConversions.asScalaBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Future, future }

import org.scaloid.common._

import com.google.android.gms.maps.{ SupportMapFragment, CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.{ Context, DialogInterface, Intent }
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.{ Message, Handler, Bundle }
import android.preference.PreferenceManager
import android.view._
import android.widget.{ FrameLayout, LinearLayout }
import net.routestory.R
import net.routestory.display.DisplayActivity
import net.routestory.display.RouteManager
import net.routestory.model.Story
import net.routestory.parts.BitmapUtils
import net.routestory.parts.GotoDialogFragments
import net.routestory.parts.HapticButton
import net.routestory.parts.Implicits.func2OnCancelListener
import net.routestory.parts.StoryActivity
import ViewGroup.LayoutParams._
import org.macroid.Transforms._
import scala.ref.WeakReference
import android.util.Log
import net.routestory.parts.Transforms._
import android.view.View.OnLongClickListener

object RecordActivity {
  val REQUEST_CODE_TAKE_PICTURE = 0
  val REQUEST_CODE_TITLE_AND_TAG = 1
}

class AudioHandler(activity: WeakReference[RecordActivity]) extends Handler {
  override def handleMessage(msg: Message) {
    val path = msg.getData.getString("path")
    activity.get.get.mAudioPieces ::= (System.currentTimeMillis / 1000L, path)
  }
}

class RecordActivity extends StoryActivity {
  lazy val mStory = new Story()
  var mMedia = Map[String, (String, String)]()
  var mAudioPieces = List[(Long, String)]()

  var mProgressDialog: ProgressDialog = null
  var mLocationListener: LocationListener = null

  lazy val mMap = findFrag[SupportMapFragment](Tag.recordingMap).getMap
  lazy val mRouteManager = new RouteManager(mMap, mStory)

  var mManMarker: Marker = null

  //var mAudioTracker: AudioTracker = null
  var mAudioTrackerThread: Thread = _
  var mToggleAudio: Boolean = false
  var mAudioProcessingTask = Future.successful(())

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setContentView(l[VerticalLinearLayout](
      w[HapticButton] ~> text("Add stuff") ~> largeText ~>
        layoutParams(MATCH_PARENT, WRAP_CONTENT) ~>
        On.click(new AddMediaDialogFragment().show(getSupportFragmentManager, Tag.addMedia)),
      l[LinearLayout](
        fragment(SupportMapFragment.newInstance(), Id.map, Tag.recordingMap)
      )
    ))

    // setup action bar
    bar.setDisplayShowTitleEnabled(false)
    bar.setDisplayShowHomeEnabled(false)
  }

  var started = false

  override def onStart() {
    super.onStart()
    if (started) return
    if (!GotoDialogFragments.ensureGPS(this)) return
    started = true

    // toggle automatic audio recording
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    mToggleAudio = prefs.getBoolean("pref_autoaudio", true)

    // setup the map
    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)

    // start tracking
    mProgressDialog = new ProgressDialog(ctx) {
      setIndeterminate(true)
      setMessage(getResources.getString(R.string.message_waitingforlocation))
      setCancelable(true)
      setCanceledOnTouchOutside(false)
      setOnCancelListener { d: DialogInterface ⇒
        giveUp()
      }
      show()
    }

    // recording starts when we receive the first location
    trackLocation()
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    super.onOptionsItemSelected(item)

    vibrator.vibrate(30) // vibrate for 30ms

    item.getItemId match {
      case R.id.stopRecord ⇒ {
        new AlertDialog.Builder(ctx) {
          setMessage(R.string.message_stoprecord)
          setPositiveButton(R.string.button_yes, {
            cleanUp(saving = true)
            mAudioProcessingTask = addAudio()
            mStory.end()
            val intent = SIntent[DescriptionActivity]
            startActivityForResult(intent, RecordActivity.REQUEST_CODE_TITLE_AND_TAG)
          })
          setNegativeButton(R.string.button_no, {})
          create()
        }.show()
        true
      }
      case R.id.toggleAudio ⇒ {
        mToggleAudio = !mToggleAudio
        if (mToggleAudio) trackAudio()
        else untrackAudio()
        invalidateOptionsMenu()
        true
      }
      case _ ⇒ false
    }
  }

  def giveUp() {
    new AlertDialog.Builder(ctx) {
      setMessage(R.string.message_giveup)
      setPositiveButton(R.string.button_yes, {
        cleanUp(saving = false)
        finish()
      })
      setNegativeButton(R.string.button_no, {
        if (mRouteManager.isEmpty) {
          mProgressDialog.show()
        }
      })
      create()
    }.show()
  }

  /* just what is says on the tin */
  def cleanUp(saving: Boolean) {
    untrackLocation()
    untrackAudio()
    if (!saving) {
      mAudioPieces.foreach(p ⇒ new File(p._2).delete())
      mMedia.foreach { case (_, (path, _)) ⇒ new File(path).delete() }
    }
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    keyCode match {
      case KeyEvent.KEYCODE_BACK ⇒ giveUp(); false
      case _ ⇒ super.onKeyDown(keyCode, event)
    }
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.activity_record, menu)
    true
  }

  override def onPrepareOptionsMenu(menu: Menu): Boolean = {
    menu.findItem(R.id.stopRecord).setEnabled(!mRouteManager.isEmpty)
    menu.findItem(R.id.toggleAudio).setTitle(if (mToggleAudio) R.string.menu_audioon else R.string.menu_audiooff)
    true
  }

  /* location tracking routines */
  def trackLocation() {
    if (mLocationListener == null) {
      mLocationListener = new LocationListener() {
        override def onLocationChanged(location: Location) {
          if (mRouteManager.isEmpty) mStory.start()
          val pt = mStory.addLocation(System.currentTimeMillis() / 1000L, location).asLatLng
          if (mRouteManager.isEmpty) {
            // now that we know where we are, start recording!
            mProgressDialog.dismiss()
            trackAudio()
            mRouteManager.init()
            invalidateOptionsMenu()
            mMap.addMarker(new MarkerOptions()
              .position(pt)
              .icon(BitmapDescriptorFactory.fromResource(R.drawable.flag_start))
              .anchor(0.3f, 1))
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder().target(pt).tilt(45).zoom(19).build())) // TODO: zoom adaptivity
          } else {
            mRouteManager.update()
            Option(mManMarker).map(_.remove())
            mManMarker = mMap.addMarker(new MarkerOptions()
              .position(pt)
              .icon(BitmapDescriptorFactory.fromResource(R.drawable.man)))
            mMap.animateCamera(CameraUpdateFactory.newLatLng(pt))
          }
        }
        override def onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override def onProviderEnabled(provider: String) {}
        override def onProviderDisabled(provider: String) {}
      }
    }
    if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_networkloc", false)) {
      locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, mLocationListener)
    }
    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, mLocationListener)
  }
  def untrackLocation() {
    locationManager.removeUpdates(mLocationListener)
  }

  /* audio tracking routines */
  def trackAudio() {
    if (mToggleAudio) {
      Option(mAudioTrackerThread) getOrElse {
        val tracker = new AudioTracker(WeakReference(ctx), new AudioHandler(WeakReference(this)), mAudioPieces.length)
        mAudioTrackerThread = new Thread(tracker, "AudioTracker")
        mAudioTrackerThread.start()
      }
    }
  }
  def untrackAudio() = {
    Option(mAudioTrackerThread) map { t ⇒
      t.interrupt()
      future {
        t.join(1000)
        mAudioTrackerThread = null
      }
    } getOrElse {
      Future.successful[Unit](())
    }
  }

  def addAudio() = future {
    val (preview, pieces) = AudioTracker.process(ctx, mAudioPieces)
    pieces.foreach { p ⇒
      val id = mStory.addAudio(p._1, "audio/aac", "aac")
      mMedia += id → (p._2, "audio/aac")
    }
    preview.foreach { p ⇒
      val id = mStory.addAudioPreview("audio/aac", "aac")
      mMedia += id → (p, "audio/aac")
    }
    mAudioPieces = List()
  }

  /* adding voice */
  def addVoice(filename: String) {
    val id = mStory.addVoice(System.currentTimeMillis() / 1000L, "audio/m4a", "m4a")
    mMedia += id -> (filename, "audio/m4a")
    mMap.addMarker(new MarkerOptions()
      .icon(BitmapDescriptorFactory.fromResource(R.drawable.mic))
      .position(mStory.getLocation(System.currentTimeMillis() / 1000L)))
  }

  /* adding text */
  def addNote(note: String) {
    mStory.addNote(System.currentTimeMillis() / 1000L, note)
    mMap.addMarker(new MarkerOptions()
      .icon(BitmapDescriptorFactory.fromResource(R.drawable.note))
      .position(mStory.getLocation(System.currentTimeMillis() / 1000L)))
  }

  /* adding heartbeat */
  def addHeartbeat(bpm: Int) {
    mStory.addHeartbeat(System.currentTimeMillis() / 1000L, bpm)
    mMap.addMarker(new MarkerOptions()
      .icon(BitmapDescriptorFactory.fromResource(R.drawable.heart))
      .position(mStory.getLocation(System.currentTimeMillis() / 1000L)))
  }

  /* adding photos */
  def addPhoto(filename: String) {
    future {
      val downsized = BitmapUtils.decodeFile(new File(filename), 1000)
      val output = new FileOutputStream(new File(filename))
      downsized.compress(Bitmap.CompressFormat.JPEG, 100, output)
      output.close()
      val id = mStory.addPhoto(System.currentTimeMillis() / 1000L, "image/jpg", "jpg")
      mMedia += id -> (filename, "image/jpg")
      val bitmap = BitmapUtils.createScaledTransparentBitmap(downsized, 100, 0.8, false)
      downsized.recycle()
      bitmap
    } onSuccessUi {
      case bitmap ⇒
        mMap.addMarker(new MarkerOptions()
          .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
          .position(mStory.getLocation(System.currentTimeMillis() / 1000L)))
    }
  }

  def createStory() {
    app.createStory(mStory)
    var rev = mStory.getRevision
    mMedia foreach {
      case (id, (filename, contentType)) ⇒
        val stream = new FileInputStream(new File(filename))
        rev = app.updateStoryAttachment(id, stream, contentType, mStory.getId, rev)
        new File(filename).delete()
    }
    app.compactLocal()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    super.onActivityResult(requestCode, resultCode, data)
    requestCode match {
      case RecordActivity.REQUEST_CODE_TITLE_AND_TAG ⇒ {
        mStory.title = data.getStringExtra("title")
        mStory.description = data.getStringExtra("description")
        mStory.setTags(data.getStringExtra("tags"))
        mStory.isPrivate = data.getBooleanExtra("private", false)
        mStory.authorId = app.getAuthorId
        mProgressDialog.setMessage(getResources.getString(R.string.message_finishing))
        mProgressDialog.show()
        mAudioProcessingTask recover {
          case t ⇒
            t.printStackTrace()
        } map { _ ⇒
          createStory()
        } onSuccessUi {
          case _ ⇒
            mProgressDialog.dismiss()
            app.sync()
            finish()
            val intent = SIntent[DisplayActivity]
            intent.putExtra("id", mStory.getId)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } onFailureUi {
          case t ⇒
            t.printStackTrace()
            mProgressDialog.dismiss()
            toast("Something went wrong!")
            finish()
        }
      }
      case _ ⇒ ;
    }
  }
}
