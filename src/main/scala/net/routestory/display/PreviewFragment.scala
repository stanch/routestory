package net.routestory.display

import net.routestory.R
import net.routestory.RouteStoryApp
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
import com.google.android.gms.maps.model._
import org.scaloid.common._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions._
import android.app.ProgressDialog
import net.routestory.parts.RouteStoryFragment
import scala.collection.mutable
import ViewGroup.LayoutParams._
import android.util.Log
import scala.async.Async.{ async, await }
import android.graphics.Bitmap
import scala.Some

class PreviewFragment extends RouteStoryFragment {
  lazy val story = getActivity.asInstanceOf[HazStory].story
  lazy val media = getActivity.asInstanceOf[HazStory].media
  lazy val map = findFrag[SupportMapFragment](Tag.previewMap).get.getMap
  lazy val routeManager = story.mapUi(new RouteManager(map, _).init())
  lazy val mHandler = new Handler

  var playButton = slot[Button]
  var mImageView = slot[ImageView]

  var mediaPlayer: Option[MediaPlayer] = None
  var manMarker: Option[Marker] = None

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[FrameLayout](
      l[FrameLayout](
        f[SupportMapFragment](Id.map, Tag.previewMap)
      ),
      l[FrameLayout](
        w[ImageView] ~> wire(mImageView)
      ),
      l[FrameLayout](
        w[Button] ~> text(R.string.play) ~> wire(playButton) ~>
          lp(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER) ~>
          On.click {
            story zip routeManager foreachUi { case (x, y) ⇒ startPreview(x, y) }
          }
      )
    )
  }

  override def onFirstStart() {
    map.setMapType(GoogleMap.MAP_TYPE_NORMAL)

    /* Display the man */
    routeManager foreachUi { rm ⇒
      manMarker = Some(map.addMarker(new MarkerOptions()
        .position(rm.getStart)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.man))))
    }

    val display = getActivity.getWindowManager.getDefaultDisplay
    val maxSize = Math.min(display.getWidth(), display.getHeight()) / 4

    def addPhoto(location: LatLng, bitmap: Bitmap) = Option(bitmap).foreach(b ⇒ map.addMarker(new MarkerOptions()
      .position(location)
      .icon(BitmapDescriptorFactory.fromBitmap(BitmapUtils.createScaledTransparentBitmap(
        b, Math.min(Math.max(b.getWidth, b.getHeight), maxSize), 0.8, false)))
    ))

    async {
      val s = await(story)
      val m = await(media)
      await(Future.sequence(m))
      s.photos.foreach(photo ⇒ photo.get(maxSize) foreachUi {
        bitmap ⇒ addPhoto(s.getLocation(photo.timestamp), bitmap)
      })
    }
  }

  override def onEveryStart() {
    routeManager foreachUi { rm ⇒
      map.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder()
        .target(rm.getStart).tilt(90).zoom(19)
        .bearing(rm.getStartBearing).build()))
    }
  }

  override def onStop() {
    super.onStop()
    mHandler.removeCallbacksAndMessages(null)
  }

  def startPreview(s: Story, rm: RouteManager) {
    playButton ~> hide
    val start = SystemClock.uptimeMillis()
    val ratio = s.duration.toDouble / RouteStoryApp.storyPreviewDuration / 1000
    val lastLocation = s.locations.last.timestamp / ratio

    Log.d("PreviewFragment", "Preparing preview")

    s.notes foreach {
      note ⇒
        Log.d("PreviewFragment", s"Scheduling note with text “${note.text}”")
        mHandler.postAtTime({
          toast(note.text)
        }, start + (note.timestamp / ratio).toInt)
    }

    s.heartbeat foreach {
      beat ⇒
        Log.d("PreviewFragment", s"Scheduling beat with bpm “${beat.bpm}”")
        mHandler.postAtTime({
          vibrator.vibrate(beat.getVibrationPattern(4), -1)
        }, start + (beat.timestamp / ratio).toInt)
    }

    // TODO: why so many magic numbers?
    val spans = s.photos.map(_.timestamp / ratio).sorted.sliding(2).map {
      case mutable.Buffer(a, b) ⇒ (b - a).toInt
      case _ ⇒ 1000
    }.toList ::: List(1000)
    (s.photos zip spans) foreach {
      case (photo, span) ⇒
        Log.d("PreviewFragment", "Scheduling a photo")
        if (span > 600) {
          mHandler.postAtTime({
            photo.get(400) foreach { bitmap ⇒
              mImageView ~> (_.setImageBitmap(bitmap)) ~@> fadeIn(300)
              mHandler.postDelayed(mImageView ~@> fadeOut(300), List(span - 600, 1500).min)
            }
          }, start + (photo.timestamp / ratio).toInt)
        }
    }

    def move() {
      val elapsed = SystemClock.uptimeMillis() - start
      val now = s.getLocation(elapsed * ratio)

      val bearing = (List(100, 300, 500) map {
        t ⇒ rm.getBearing(now, s.getLocation((elapsed + t) * ratio))
      } zip List(0.3f, 0.4f, 0.3f) map {
        case (b, w) ⇒ b * w
      }).sum

      Log.d("PreviewFragment", "Walking")
      map.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(map.getCameraPosition).target(now).bearing(bearing).build()))
      if (elapsed < lastLocation) {
        mHandler.postDelayed(move, 300)
      }
    }
    mHandler.postDelayed(move, 300)

    def walk() {
      val elapsed = SystemClock.uptimeMillis() - start
      val now = s.getLocation(elapsed * ratio)
      manMarker.map(_.remove())
      manMarker = Some(map.addMarker(new MarkerOptions()
        .position(now)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.man))))
      if (elapsed < lastLocation) {
        mHandler.postDelayed(walk, 100)
      }
    }
    mHandler.postDelayed(walk, 100)

    mHandler.postDelayed({
      rewind()
    }, (RouteStoryApp.storyPreviewDuration + 3) * 1000)

    playAudio(s)
  }

  def rewind() {
    playButton ~> show
    routeManager foreachUi { rm ⇒
      map.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder()
        .target(rm.getStart).tilt(90).zoom(19)
        .bearing(rm.getStartBearing).build()))
      manMarker.map(_.remove())
      manMarker = Some(map.addMarker(new MarkerOptions()
        .position(rm.getStart)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.man))))
    }
  }

  def playAudio(s: Story) {
    mediaPlayer = Some(new MediaPlayer)
    Option(s.audioPreview).map(_.get.foreachUi { file ⇒
      try {
        mediaPlayer.foreach(_.setDataSource(file.getAbsolutePath))
        mediaPlayer.foreach(_.prepare())
        mediaPlayer.foreach(_.start())
      } catch {
        case e: Throwable ⇒ e.printStackTrace()
      }
    })
  }
}
