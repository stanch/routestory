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
import android.widget.FrameLayout.LayoutParams
import scala.collection.mutable
import android.view.animation.AlphaAnimation
import ViewGroup.LayoutParams._
import android.util.Log
import scala.async.Async.{ async, await }

class PreviewFragment extends StoryFragment {
  lazy val mStory = getActivity.asInstanceOf[HazStory].getStory
  lazy val mMap = findFrag[SupportMapFragment](Tag.previewMap).get.getMap
  lazy val mRouteManager = async {
    val story = await(mStory)
    await(Ui(new RouteManager(mMap, story).init()))
  }
  lazy val mHandler = new Handler

  var mPlayButton = slot[Button]
  var mImageView = slot[ImageView]

  var mMediaPlayer: MediaPlayer = _
  var mPositionMarker: Marker = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[FrameLayout](
      l[FrameLayout](
        fragment(SupportMapFragment.newInstance(), Id.map, Tag.previewMap)
      ),
      l[FrameLayout](
        w[ImageView] ~> wire(mImageView)
      ),
      l[FrameLayout](
        w[Button] ~> text(R.string.play) ~> wire(mPlayButton) ~>
          lp(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER) ~>
          On.click {
            mStory zip mRouteManager onSuccessUi {
              case (x, y) ⇒ startPreview(x, y)
            }
          }
      )
    )
  }

  override def onFirstStart() {
    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)

    /* Display the man */
    async {
      val rm = await(mRouteManager)
      Ui {
        mPositionMarker = mMap.addMarker(new MarkerOptions()
          .position(rm.getStart)
          .icon(BitmapDescriptorFactory.fromResource(R.drawable.man))
        )
      }
    }

    val display = getActivity.getWindowManager.getDefaultDisplay
    val maxSize = Math.min(display.getWidth(), display.getHeight()) / 4

    async {
      val story = await(mStory)
      val progress = await(Ui(new ProgressDialog(ctx) {
        setMessage("Loading data...")
        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        setMax(story.photos.length + Option(story.audioPreview).map(x ⇒ 1).getOrElse(0))
        show()
      }))

      val photos = Future.sequence(story.photos.map(i ⇒ async {
        val pic = await(i.get(maxSize))
        Option(pic) foreach {
          bitmap ⇒
            Ui(mMap.addMarker(new MarkerOptions()
              .position(story.getLocation(i.timestamp))
              .icon(BitmapDescriptorFactory.fromBitmap(BitmapUtils.createScaledTransparentBitmap(
                bitmap, Math.min(Math.max(bitmap.getWidth, bitmap.getHeight), maxSize), 0.8, false)
              ))
            ))
        }
        progress.incrementProgressBy(1)
      }))

      val audio = Option(story.audioPreview).map(_.get.onSuccessUi {
        case _ ⇒
          progress.incrementProgressBy(1)
      }).getOrElse(Future.successful(()))

      await(photos zip audio)
      Ui(progress.dismiss())
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
    mPlayButton ~> hide
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

    val fadeIn = show +@ anim(new AlphaAnimation(0, 1), duration = 300)
    val fadeOut = anim(new AlphaAnimation(1, 0), duration = 300) @+ hide
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
                mImageView ~> { x: ImageView ⇒ x.setImageBitmap(bitmap) } ~@> fadeIn
                mHandler.postDelayed({
                  mImageView ~@> fadeOut
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
    mPlayButton ~> show
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
