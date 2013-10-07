package net.routestory.display

import net.routestory.R
import net.routestory.model.Story
import net.routestory.parts.{ Styles, BitmapUtils, RouteStoryFragment }
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.{ SeekBar, Button, FrameLayout, ImageView }
import com.google.android.gms.maps.{ SupportMapFragment, CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model._
import org.scaloid.common._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions._
import scala.collection.mutable
import ViewGroup.LayoutParams._
import android.util.Log
import scala.async.Async.{ async, await }
import android.graphics.Bitmap
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import android.widget.SeekBar.OnSeekBarChangeListener
import rx.Var

object DiveFragment {
  val photoDuration = 1500
  val photoFade = 300
}

class DiveFragment extends RouteStoryFragment {
  lazy val story = getActivity.asInstanceOf[HazStory].story
  lazy val media = getActivity.asInstanceOf[HazStory].media
  lazy val map = findFrag[SupportMapFragment](Tag.previewMap).get.getMap
  lazy val routeManager = story.mapUi(new RouteManager(map, _).init())
  lazy val display = getActivity.getWindowManager.getDefaultDisplay
  lazy val handler = new Handler

  val ratio = Var(10.5 / 1000) // account for s → ms
  val cue = Var(0.0)
  val playing = Var(false)

  var playBig = slot[Button]
  var imageView = slot[ImageView]
  var play = slot[Button]
  var pause = slot[Button]
  var seekBar = slot[SeekBar]

  var mediaPlayer: Option[MediaPlayer] = None
  var manMarker: Option[Marker] = None

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[VerticalLinearLayout](
      l[FrameLayout](
        l[FrameLayout](
          f[SupportMapFragment](Id.map, Tag.previewMap)
        ),
        l[FrameLayout](
          w[ImageView] ~> wire(imageView)
        ),
        l[FrameLayout](
          w[Button] ~> wire(playBig) ~>
            lp(80 dip, 80 dip, Gravity.CENTER) ~>
            Styles.bg(R.drawable.play_big) ~>
            story.map(s ⇒ On.click {
              playing.update(true)
              start(s)
            })
        )
      ) ~> lp(WRAP_CONTENT, WRAP_CONTENT, 1.0f),
      l[HorizontalLinearLayout](
        w[Button] ~> Styles.bg(R.drawable.play) ~> lp(32 dip, 32 dip) ~>
          wire(play) ~> story.map(s ⇒ On.click { playing.update(true); start(s) }),
        w[Button] ~> Styles.bg(R.drawable.pause) ~> lp(32 dip, 32 dip) ~>
          wire(pause) ~> story.map(s ⇒ On.click { playing.update(false); stop(); }) ~> hide,
        w[SeekBar] ~> wire(seekBar) ~> lp(MATCH_PARENT, WRAP_CONTENT)
      ) ~> padding(left = 16 dip, right = 8 dip, top = 8 dip, bottom = 8 dip) ~> (_.setBackgroundColor(0xff101010))
    )
  }

  override def onFirstStart() {
    map.setMapType(GoogleMap.MAP_TYPE_NORMAL)

    /* Display the man */
    routeManager foreachUi { rm ⇒
      manMarker = Some(map.addMarker(new MarkerOptions()
        .position(rm.getStart.get)
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
      Ui(positionMap(s, 0, tiltZoom = true))
      val m = await(media)
      await(Future.sequence(m))
      s.photos.foreach(photo ⇒ photo.get(maxSize) foreachUi {
        bitmap ⇒ addPhoto(photo.getLocation, bitmap)
      })
      Ui(s.notes.foreach(note ⇒ map.addMarker(new MarkerOptions()
        .position(note.getLocation)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.text_note))
      )))
      Ui(s.heartbeat.foreach(beat ⇒ map.addMarker(new MarkerOptions()
        .position(beat.getLocation)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.heart))
      )))
    }
  }

  override def onEveryStart() {
    story foreachUi { s ⇒
      seekBar ~> (_.setMax(seekBar.get.getWidth)) ~> (_.setOnSeekBarChangeListener(new OnSeekBarChangeListener {
        def onProgressChanged(bar: SeekBar, position: Int, fromUser: Boolean) {
          cue.update(1.0 * position / bar.getMax)
          if (fromUser) {
            val ts = Math.ceil(cue.now * s.duration / ratio.now).toInt
            positionMap(s, ts, animate = false)
            positionMan(s, ts)
          }
        }
        def onStopTrackingTouch(bar: SeekBar) {
          if (playing.now) start(s)
        }
        def onStartTrackingTouch(bar: SeekBar) {
          stop()
        }
      }))
    }
  }

  override def onStop() {
    super.onStop()
    handler.removeCallbacksAndMessages(null)
  }

  def positionMap(s: Story, ts: Long, animate: Boolean = true, tiltZoom: Boolean = false) {
    val now = s.getLocation(ts * ratio.now)
    val bearing = List(3800, 4200).map(t ⇒ RouteManager.getBearing(now, s.getLocation((ts + t) * ratio.now))).sum / 2
    val position = CameraUpdateFactory.newCameraPosition(if (tiltZoom) {
      CameraPosition.builder().target(now).tilt(90).zoom(19).bearing(bearing).build()
    } else {
      CameraPosition.builder(map.getCameraPosition).target(now).bearing(bearing).build()
    })
    if (animate) map.animateCamera(position) else map.moveCamera(position)
  }

  def positionMan(s: Story, ts: Long) {
    val now = s.getLocation(ts * ratio.now)
    manMarker.map(_.setPosition(now))
  }

  def stop() {
    pause ~> hide
    play ~> show
    imageView ~> hide
    handler.removeCallbacksAndMessages(null)
  }

  def start(s: Story) {
    playBig ~> hide
    play ~> hide
    pause ~> show
    val duration = Math.ceil(s.duration / ratio.now).toInt
    val from = Math.ceil(cue.now * duration)
    val start = SystemClock.uptimeMillis

    s.notes foreach { note ⇒
      val at = note.timestamp / ratio.now - from
      if (at > 0) handler.postAtTime(toast(note.text), start + at.toInt)
    }

    s.heartbeat foreach { beat ⇒
      val at = beat.timestamp / ratio.now - from
      if (at > 0) handler.postAtTime(vibrator.vibrate(beat.getVibrationPattern(4), -1), start + at.toInt)
    }

    import DiveFragment._
    // calculate timespans between photos
    val spans = s.photos.map(_.timestamp / ratio.now).sliding(2).map {
      case mutable.Buffer(a, b) ⇒ (b - a).toInt
      case _ ⇒ photoDuration
    }.toList ::: List(photoDuration)
    // only show a photo if there’s time to fade it in and out
    (s.photos zip spans) foreach {
      case (photo, span) if span > 2 * photoFade && (photo.timestamp / ratio.now > from) ⇒
        handler.postAtTime({
          photo.get(Math.min(display.getWidth, display.getHeight)) foreach { bitmap ⇒
            imageView ~> (_.setImageBitmap(bitmap)) ~@> fadeIn(photoFade)
            handler.postDelayed(imageView ~@> fadeOut(photoFade), List(span - 2 * photoFade, photoDuration).min)
          }
        }, start + (photo.timestamp / ratio.now - from).toInt)
      case _ ⇒
    }

    def move() {
      positionMap(s, SystemClock.uptimeMillis - start + from.toInt)
      handler.postDelayed(move, 500)
    }
    handler.postAtTime(move, start)

    def walk() {
      val ts = SystemClock.uptimeMillis - start + from.toInt
      seekBar ~> (_.setProgress((ts * seekBar.get.getMax / duration).toInt))
      positionMan(s, ts)
      if (ts <= duration) handler.postDelayed(walk, 100) else handler.postDelayed(rewind(s), 1000)
    }
    handler.postAtTime(walk, start)
  }

  def rewind(s: Story) {
    playBig ~> show
    play ~> show
    pause ~> hide
    playing.update(false)
    seekBar ~> (_.setProgress(0))
    stop()
    positionMap(s, 0, tiltZoom = true)
    positionMan(s, 0)
  }

  //  def playAudio(a: Story) {
  //    mediaPlayer = Some(new MediaPlayer)
  //    Option(s.audioPreview).map(_.get.foreachUi { file ⇒
  //      try {
  //        mediaPlayer.foreach(_.setDataSource(file.getAbsolutePath))
  //        mediaPlayer.foreach(_.prepare())
  //        mediaPlayer.foreach(_.start())
  //      } catch {
  //        case e: Throwable ⇒ e.printStackTrace()
  //      }
  //    })
  //  }
}
