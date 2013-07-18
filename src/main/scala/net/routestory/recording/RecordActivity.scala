package net.routestory.recording

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import scala.collection.JavaConversions.asScalaBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

import org.scaloid.common._

import com.google.android.gms.maps.{ MapFragment, CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view._
import android.widget.{ LinearLayout, FrameLayout }
import net.routestory.R
import net.routestory.display.DisplayActivity
import net.routestory.display.RouteManager
import net.routestory.model.Story
import net.routestory.parts.BitmapUtils
import net.routestory.parts.GotoDialogFragments
import net.routestory.parts.HapticButton
import net.routestory.parts.Implicits.func2OnCancelListener
import net.routestory.parts.StoryActivity

object RecordActivity {
    val REQUEST_CODE_TAKE_PICTURE = 0
    val REQUEST_CODE_TITLE_AND_TAG = 1
}

class RecordActivity extends StoryActivity {
    lazy val mStory = new Story()
    var mMedia = Map[String, (String, String)]()

    var mProgressDialog: ProgressDialog = null
    var mLocationListener: LocationListener = null

    lazy val mMap = findFrag("recording_map").asInstanceOf[MapFragment].getMap
    lazy val mRouteManager = new RouteManager(mMap, mStory)

    var mManMarker: Marker = null

    var mAudioTracker: AudioTracker = null
    var mAudioTrackerThread: Thread = null
    var mToggleAudio: Boolean = false

    override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)

        val view = new LinearLayout(ctx) {
            setOrientation(LinearLayout.VERTICAL)
            this += new FrameLayout(ctx) {
                this += new FrameLayout(ctx).id(1)
            }
            this += new HapticButton(ctx) {
                setText("Add media")
                setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
                setOnClickListener { v: View ⇒
                    new AddMediaDialogFragment().show(getFragmentManager, "add_media")
                }
            }
        }

        if (findFrag("recording_map") == null) {
            val mapFragment = MapFragment.newInstance()
            val fragmentTransaction = getFragmentManager.beginTransaction()
            fragmentTransaction.add(1, mapFragment, "recording_map")
            fragmentTransaction.commit()
        }

        setContentView(view)

        // setup action bar
        bar.setDisplayShowTitleEnabled(false)
        bar.setDisplayShowHomeEnabled(false)
    }

    override def onFirstStart() {
        if (!GotoDialogFragments.ensureGPS(this)) return

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
                        cleanUp()
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
                if (mToggleAudio) unpauseAudio()
                else pauseAudio()
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
                cleanUp()
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
    def cleanUp() {
        untrackLocation()
        untrackAudio()
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
        menu.findItem(R.id.toggleAudio).setTitle(
            if (mAudioTrackerThread != null && !mAudioTracker.isPaused) R.string.menu_audioon else R.string.menu_audiooff)
        true
    }

    /* location tracking routines */
    def trackLocation() {
        if (mLocationListener == null) {
            mLocationListener = new LocationListener() {
                override def onLocationChanged(location: Location) {
                    val pt = mStory.addLocation(System.currentTimeMillis() / 1000L, location).asLatLng()
                    if (mRouteManager.isEmpty) {
                        // now that we know where we are, start recording!
                        mProgressDialog.dismiss()
                        mStory.start()
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
                        if (mManMarker != null) {
                            mManMarker.remove()
                        }
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
        //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, mLocationListener)
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, mLocationListener)
    }
    def untrackLocation() {
        locationManager.removeUpdates(mLocationListener)
    }

    /* audio tracking routines */
    def trackAudio() {
        mAudioTracker = new AudioTracker(getApplicationContext)
        if (!mToggleAudio) mAudioTracker.pause()
        mAudioTrackerThread = new Thread(mAudioTracker, "AudioTracker")
        mAudioTrackerThread.start()
    }
    def pauseAudio() {
        if (mAudioTrackerThread != null) {
            mAudioTracker.pause()
        }
    }
    def unpauseAudio() {
        if (mAudioTrackerThread != null && mToggleAudio) {
            mAudioTracker.unpause()
        }
    }
    def untrackAudio() {
        // TODO: this should delete all audio samples if we quit without finishing
        if (mAudioTrackerThread != null) {
            mAudioTracker.unpause()
            mAudioTrackerThread.interrupt()
        }
    }
    def addAudio() {
        mAudioTracker.output foreach { piece ⇒
            val id = if (piece.timestamp > 0) {
                mStory.addAudio(piece.timestamp, "audio/aac", "aac")
            } else {
                mStory.addAudioPreview("audio/aac", "aac")
            }
            mMedia += id -> (piece.filename, "audio/aac")
        }
    }

    /* adding voice */
    def addVoice(filename: String) {
        unpauseAudio()
        val id = mStory.addVoice(System.currentTimeMillis() / 1000L, "audio/m4a", "m4a")
        mMedia += id -> (filename, "audio/m4a")
        mMap.addMarker(new MarkerOptions()
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.mic))
            .position(mStory.getLocation(System.currentTimeMillis() / 1000L)))
    }

    /* adding text */
    def addNote(note: String) {
        unpauseAudio()
        mStory.addNote(System.currentTimeMillis() / 1000L, note)
        mMap.addMarker(new MarkerOptions()
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.note))
            .position(mStory.getLocation(System.currentTimeMillis() / 1000L)))
    }

    /* adding heartbeat */
    def addHeartbeat(bpm: Int) {
        unpauseAudio()
        mStory.addHeartbeat(System.currentTimeMillis() / 1000L, bpm)
        mMap.addMarker(new MarkerOptions()
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.heart))
            .position(mStory.getLocation(System.currentTimeMillis() / 1000L)))
    }

    /* adding photos */
    def addPhoto(filename: String) {
        unpauseAudio()
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
                future {
                    mAudioTrackerThread.join()
                    addAudio()
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
                }
            }
            case _ ⇒ ;
        }
    }
}
