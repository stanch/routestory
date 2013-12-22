package net.routestory.display

import net.routestory.R
import net.routestory.parts.{ FragmentData, BitmapUtils, RouteStoryFragment }
import android.media.MediaPlayer
import android.os.{ Vibrator, Bundle, Handler, SystemClock }
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget._
import com.google.android.gms.maps.{ SupportMapFragment, CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model._
import scala.concurrent.ExecutionContext.Implicits.global
import ViewGroup.LayoutParams._
import android.util.Log
import scala.async.Async.{ async, await }
import android.graphics.Bitmap
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import android.widget.SeekBar.OnSeekBarChangeListener
import rx.Var
import org.macroid.contrib.ExtraTweaks
import net.routestory.parts.Implicits._
import android.content.Context
import scala.concurrent.Future
import net.routestory.model.Story
import net.routestory.model.Story._
import scala.ref.WeakReference
import net.routestory.parts.Styles._

object DiveFragment {
  val photoDuration = 1500
  val photoFade = 300
}

class DiveFragment extends RouteStoryFragment with FragmentData[Future[Story]] with ExtraTweaks {
  lazy val story = getFragmentData
  lazy val map = findFrag[SupportMapFragment](Tag.previewMap).get.getMap
  lazy val mapManager = new RouteMapManager(map, displaySize, WeakReference(getActivity))()
  lazy val handler = new Handler

  // playback vars
  val ratio = Var(10.5 / 1000) // account for s → ms
  val cue = Var(0.0)
  val playing = Var(false)

  // widgets
  var layout = slot[LinearLayout]
  var playBig = slot[Button]
  var imageView = slot[ImageView]
  var play = slot[Button]
  var pause = slot[Button]
  var seekBar = slot[SeekBar]

  var mediaPlayer: Option[MediaPlayer] = None

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[VerticalLinearLayout](
      l[FrameLayout](
        l[FrameLayout](
          f[SupportMapFragment].framed(Id.map, Tag.previewMap)
        ),
        l[FrameLayout](
          w[ImageView] ~> wire(imageView)
        ),
        l[FrameLayout](
          w[Button] ~> wire(playBig) ~>
            lp(80 dp, 80 dp, Gravity.CENTER) ~>
            Bg.res(R.drawable.play_big) ~>
            story.map(s ⇒ On.click {
              playing.update(true)
              start(s.chapters(0))
            })
        )
      ) ~> lp(WRAP_CONTENT, WRAP_CONTENT, 1.0f),
      l[HorizontalLinearLayout](
        w[Button] ~> Bg.res(R.drawable.play) ~> lp(32 dp, 32 dp) ~>
          wire(play) ~> story.map(s ⇒ On.click { playing.update(true); start(s.chapters(0)) }),
        w[Button] ~> Bg.res(R.drawable.pause) ~> lp(32 dp, 32 dp) ~>
          wire(pause) ~> story.map(s ⇒ On.click { playing.update(false); stop(); }) ~> hide,
        w[SeekBar] ~> wire(seekBar) ~> lp(MATCH_PARENT, WRAP_CONTENT)
      ) ~> padding(left = 16 dp, right = 8 dp, top = 8 dp, bottom = 8 dp)
    ) ~> wire(layout)
  }

  override def onFirstStart() {
    map.setMapType(GoogleMap.MAP_TYPE_NORMAL)

    story foreachUi { s ⇒
      val chapter = s.chapters(0)
      positionMap(chapter, 0, tiltZoom = true)
      mapManager.add(chapter)
      mapManager.updateMan(mapManager.start.get)
    }
  }

  override def onEveryStart() {
    story foreachUi { s ⇒
      val chapter = s.chapters(0)
      seekBar ~>
        (_.setMax(seekBar.get.getWidth)) ~>
        (_.setOnSeekBarChangeListener(new OnSeekBarChangeListener {
          def onProgressChanged(bar: SeekBar, position: Int, fromUser: Boolean) {
            cue.update(1.0 * position / bar.getMax)
            if (fromUser) {
              val ts = Math.ceil(cue.now * chapter.duration / ratio.now).toInt
              positionMap(chapter, ts, animate = false)
              positionMan(chapter, ts)
            }
          }
          def onStopTrackingTouch(bar: SeekBar) {
            if (playing.now) start(chapter)
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

  def positionMap(chapter: Chapter, ts: Long, animate: Boolean = true, tiltZoom: Boolean = false) {
    val now = chapter.locationAt(ts * ratio.now)
    val bearing = List(3800, 4200).map(t ⇒ now.bearingTo(chapter.locationAt((ts + t) * ratio.now))).sum / 2
    val position = CameraUpdateFactory.newCameraPosition(if (tiltZoom) {
      CameraPosition.builder().target(now).tilt(90).zoom(19).bearing(bearing).build()
    } else {
      CameraPosition.builder(map.getCameraPosition).target(now).bearing(bearing).build()
    })
    if (animate) map.animateCamera(position) else map.moveCamera(position)
  }

  def positionMan(chapter: Chapter, ts: Long) {
    val now = chapter.locationAt(ts * ratio.now)
    mapManager.updateMan(now)
  }

  def stop() {
    pause ~> hide
    play ~> show
    imageView ~> hide
    handler.removeCallbacksAndMessages(null)
    layout ~> (_.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE))
  }

  def start(chapter: Chapter) {
    playBig ~> hide
    play ~> hide
    pause ~> show
    layout ~> immerse
    val duration = Math.ceil(chapter.duration / ratio.now).toInt
    val from = Math.ceil(cue.now * duration)
    val start = SystemClock.uptimeMillis

    lazy val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]

    var photos = Vector[Photo]()

    chapter.media foreach { m ⇒
      val at = m.timestamp / ratio.now - from
      if (at > 0) m match {
        case TextNote(_, text) ⇒
          handler.postAtTime(toast(text) ~> fry, start + at.toInt)
        case beat: Heartbeat ⇒
          handler.postAtTime(vibrator.vibrate(beat.vibrationPattern(4), -1), start + at.toInt)
        case photo: Photo ⇒
          photos :+= photo
        case _ ⇒
      }
    }

    import DiveFragment._
    // calculate timespans between photos
    val spans = photos.map(_.timestamp / ratio.now).sliding(2).map {
      case Vector(a, b) ⇒ (b - a).toInt
      case _ ⇒ photoDuration
    }.toVector :+ photoDuration
    // only show a photo if there’s time to fade it in and out
    (photos zip spans) foreach {
      case (photo, span) if span > 2 * photoFade ⇒
        handler.postAtTime({
          photo.fetchAndLoad(displaySize.min) foreach { bitmap ⇒
            imageView ~> (_.setImageBitmap(bitmap)) ~@> fadeIn(photoFade)
            handler.postDelayed(imageView ~@> fadeOut(photoFade), List(span - 2 * photoFade, photoDuration).min)
          }
        }, start + (photo.timestamp / ratio.now - from).toInt)
      case _ ⇒
    }

    def move() {
      positionMap(chapter, SystemClock.uptimeMillis - start + from.toInt)
      handler.postDelayed(move, 500)
    }
    handler.postAtTime(move, start)

    def walk() {
      val ts = SystemClock.uptimeMillis - start + from.toInt
      seekBar ~> (_.setProgress((ts * seekBar.get.getMax / duration).toInt))
      positionMan(chapter, ts)
      if (ts <= duration) handler.postDelayed(walk, 100) else handler.postDelayed(rewind(chapter), 1000)
    }
    handler.postAtTime(walk, start)
  }

  def rewind(chapter: Chapter) {
    playBig ~> show
    play ~> show
    pause ~> hide
    playing.update(false)
    seekBar ~> (_.setProgress(0))
    stop()
    positionMap(chapter, 0, tiltZoom = true)
    positionMan(chapter, 0)
  }
}
