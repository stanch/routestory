package net.routestory.display

import net.routestory.R
import net.routestory.StoryApplication
import net.routestory.model.Story
import net.routestory.parts.BitmapUtils
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import com.google.android.gms.maps.{ SupportMapFragment, CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.scaloid.common._
import net.routestory.parts.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions._
import android.app.ProgressDialog
import net.routestory.parts.StoryFragment
import akka.dataflow._
import android.widget.FrameLayout.LayoutParams
import scala.collection.mutable
import android.view.animation.AlphaAnimation
import net.routestory.parts.Animation._
import ViewGroup.LayoutParams._
import android.util.Log

class PreviewFragment extends StoryFragment {
  lazy val mStory = getActivity.asInstanceOf[HazStory].getStory
  lazy val mMap = findFrag[SupportMapFragment](Tag.previewMap).getMap
  lazy val mRouteManager = flow {
    val story = await(mStory)
    switchToUiThread()
    new RouteManager(mMap, story).init()
  }
  lazy val mHandler = new Handler

  def mPlayButton = findView[Button](Id.play)

  def mImageView = findView[ImageView](Id.imageView)

  var mMediaPlayer: MediaPlayer = _
  var mPositionMarker: Marker = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    new FrameLayout(ctx) {
      this += new FrameLayout(ctx) {
        this += fragment(SupportMapFragment.newInstance(), Id.map, Tag.previewMap)
      }
      this += new FrameLayout(ctx) {
        this += new ImageView(ctx) {
          setId(Id.imageView)
        }
      }
      this += new FrameLayout(ctx) {
        this += new Button(ctx) {
          setText(R.string.play)
          setId(Id.play)
          setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
          setOnClickListener {
            v: View ⇒
              mStory zip mRouteManager onSuccessUi {
                case (x, y) ⇒ startPreview(x, y)
              }
          }
        }
      }
    }
  }

  override def onFirstStart() {
    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)

    /* Display the man */
    flow {
      val rm = await(mRouteManager)
      switchToUiThread()
      mPositionMarker = mMap.addMarker(new MarkerOptions()
        .position(rm.getStart)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.man))
      )
    }

    val display = getActivity.getWindowManager.getDefaultDisplay
    val maxSize = Math.min(display.getWidth(), display.getHeight()) / 4

    flow {
      val story = await(mStory)

      switchToUiThread()
      val progress = new ProgressDialog(ctx) {
        setMessage("Loading data...")
        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        setMax(story.photos.length + Option(story.audioPreview).map(x ⇒ 1).getOrElse(0))
        show()
      }

      val photos = Future.sequence(story.photos.map(i ⇒ flow {
        val pic = await(i.get(maxSize))
        switchToUiThread()
        Option(pic) foreach {
          bitmap ⇒
            mMap.addMarker(new MarkerOptions()
              .position(story.getLocation(i.timestamp))
              .icon(BitmapDescriptorFactory.fromBitmap(BitmapUtils.createScaledTransparentBitmap(
                bitmap, Math.min(Math.max(bitmap.getWidth, bitmap.getHeight), maxSize), 0.8, false)
              ))
            )
        }
        progress.incrementProgressBy(1)
      }))

      val audio = Option(story.audioPreview).map(_.get.onSuccessUi {
        case _ ⇒
          progress.incrementProgressBy(1)
      }).getOrElse(Future.successful(()))

      await(photos zip audio)
      switchToUiThread()
      progress.dismiss()
    }
  }

  override def onEveryStart() {
    mRouteManager onSuccessUi {
      case rm ⇒
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder()
          .target(rm.getStart).tilt(90).zoom(19)
          .bearing(rm.getStartBearing).build()
        ))
    }
  }

  override def onStop() {
    super.onStop()
    mHandler.removeCallbacksAndMessages(null)
  }

  def startPreview(story: Story, rm: RouteManager) {
    mPlayButton.setVisibility(View.INVISIBLE)
    val start = SystemClock.uptimeMillis()
    val ratio = story.duration.toDouble / StoryApplication.storyPreviewDuration / 1000
    val lastLocation = story.locations.last.timestamp / ratio

    Log.d("PreviewFragment", "Preparing preview")

    story.notes foreach {
      note ⇒
        Log.d("PreviewFragment", s"Scheduling note with text “${note.text}”")
        mHandler.postAtTime({
          toast(note.text)
        }, start + (note.timestamp / ratio).toInt)
    }

    story.heartbeat foreach {
      beat ⇒
        Log.d("PreviewFragment", s"Scheduling beat with bpm “${beat.bpm}”")
        mHandler.postAtTime({
          vibrator.vibrate(beat.getVibrationPattern(4), -1)
        }, start + (beat.timestamp / ratio).toInt)
    }

    def fadeIn(view: View) = new AlphaAnimation(0, 1).duration(300).runOn(view)
    def fadeOut(view: View) = new AlphaAnimation(1, 0).duration(300).runOn(view, hideOnFinish = true)
    val spans = story.photos.map(_.timestamp / ratio).sorted.sliding(2).map {
      case mutable.Buffer(a, b) ⇒ (b - a).toInt
      case _ ⇒ 1000
    }.toList ::: List(1000)
    (story.photos zip spans) foreach {
      case (photo, span) ⇒
        Log.d("PreviewFragment", "Scheduling a photo")
        if (span > 600) {
          mHandler.postAtTime({
            photo.get(400) foreach {
              bitmap ⇒
                runOnUiThread(mImageView.setImageBitmap(bitmap))
                fadeIn(mImageView)
                mHandler.postDelayed({
                  fadeOut(mImageView)
                }, List(span - 600, 1500).min)
            }
          }, start + (photo.timestamp / ratio).toInt)
        }
    }

    def move() {
      val elapsed = SystemClock.uptimeMillis() - start
      val now = story.getLocation(elapsed * ratio)

      val bearing = (List(100, 300, 500) map {
        t ⇒
          rm.getBearing(now, story.getLocation((elapsed + t) * ratio))
      } zip List(0.3f, 0.4f, 0.3f) map {
        case (b, w) ⇒
          b * w
      }).sum

      Log.d("PreviewFragment", "Walking")
      mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(mMap.getCameraPosition).target(now).bearing(bearing).build()))
      if (elapsed < lastLocation) {
        mHandler.postDelayed(move, 300)
      }
    }
    mHandler.postDelayed(move, 300)

    def walk() {
      val elapsed = SystemClock.uptimeMillis() - start
      val now = story.getLocation(elapsed * ratio)
      mPositionMarker.remove()
      mPositionMarker = mMap.addMarker(new MarkerOptions()
        .position(now)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.man)))
      if (elapsed < lastLocation) {
        mHandler.postDelayed(walk, 100)
      }
    }
    mHandler.postDelayed(walk, 100)

    mHandler.postDelayed({
      rewind()
    }, (StoryApplication.storyPreviewDuration + 3) * 1000)

    playAudio(story)
  }

  def rewind() {
    mPlayButton.setVisibility(View.VISIBLE)
    mRouteManager onSuccessUi {
      case rm ⇒
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder()
          .target(rm.getStart).tilt(90).zoom(19)
          .bearing(rm.getStartBearing).build()))
        mPositionMarker.remove()
        mPositionMarker = mMap.addMarker(new MarkerOptions()
          .position(rm.getStart)
          .icon(BitmapDescriptorFactory.fromResource(R.drawable.man)))
    }
  }

  def playAudio(story: Story) {
    mMediaPlayer = new MediaPlayer()
    if (story.audioPreview == null) return
    story.audioPreview.get onSuccessUi {
      case file ⇒
        if (file == null) return
        try {
          mMediaPlayer.setDataSource(file.getAbsolutePath)
          mMediaPlayer.prepare()
          mMediaPlayer.start()
        } catch {
          case e: Throwable ⇒ e.printStackTrace()
        }
    }
  }
}
