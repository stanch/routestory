package net.routestory.display

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import android.content.Context
import android.media.MediaPlayer
import android.os.{ Bundle, Handler, SystemClock, Vibrator }
import android.view.{ Gravity, LayoutInflater, View, ViewGroup }
import android.view.ViewGroup.LayoutParams._
import android.widget._
import android.widget.SeekBar.OnSeekBarChangeListener

import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap, SupportMapFragment }
import com.google.android.gms.maps.model.{ f ⇒ _, _ }
import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import rx.Var

import net.routestory.R
import net.routestory.model.Story
import net.routestory.model.Story._
import net.routestory.ui.RouteStoryFragment
import net.routestory.ui.Styles._
import net.routestory.util.FragmentData
import net.routestory.util.Implicits._
import org.macroid.{ IdGeneration, Tweak }

object DiveFragment {
  val photoDuration = 1500
  val photoFade = 300
}

class DiveFragment extends RouteStoryFragment with FragmentData[Future[Story]] with IdGeneration {
  lazy val story = getFragmentData
  lazy val map = findFrag[SupportMapFragment](Tag.previewMap).get.getMap
  lazy val mapManager = new RouteMapManager(map, displaySize)()
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
          w[Button] ~>
            wire(playBig) ~>
            lp(80 dp, 80 dp, Gravity.CENTER) ~>
            Bg.res(R.drawable.play_big) ~>
            story.map(s ⇒ On.click { playing.update(true); start(s.chapters(0)) })
        )
      ) ~> lp(WRAP_CONTENT, WRAP_CONTENT, 1.0f),
      l[HorizontalLinearLayout](
        w[Button] ~>
          Bg.res(R.drawable.play) ~>
          lp(32 dp, 32 dp) ~>
          wire(play) ~>
          story.map(s ⇒ On.click { playing.update(true); start(s.chapters(0)) }),
        w[Button] ~>
          Bg.res(R.drawable.pause) ~>
          lp(32 dp, 32 dp) ~>
          wire(pause) ~>
          story.map(s ⇒ On.click { playing.update(false); stop(); }) ~> hide,
        w[SeekBar] ~>
          wire(seekBar) ~>
          lp(MATCH_PARENT, WRAP_CONTENT)
      ) ~> padding(left = 16 dp, right = 8 dp, top = 8 dp, bottom = 8 dp)
    ) ~> wire(layout)
    w[View]
  }

  var positioned = false
  override def onStart() {
    super.onStart()

    if (!positioned) {
      positioned = true
      map.setMapType(GoogleMap.MAP_TYPE_NORMAL)
      story foreachUi { s ⇒
        val chapter = s.chapters(0)
        positionMap(chapter, 0, tiltZoom = true)
        mapManager.add(chapter)
        mapManager.updateMan(mapManager.start.get)
      }
    }

    story foreachUi { s ⇒
      val chapter = s.chapters(0)
      seekBar ~> Tweak[SeekBar] { bar ⇒
        bar.setMax(bar.getWidth)
        bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener {
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
        })
      }
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
    layout ~> unImmerse
  }

  def start(chapter: Chapter) {
    playBig ~> hide
    play ~> hide
    pause ~> show
    layout ~> immerse
    val duration = Math.ceil(chapter.duration / ratio.now).toInt
    val from = Math.ceil(cue.now * duration)
    val start = SystemClock.uptimeMillis

    lazy val vibrator = getActivity.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]

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
            imageView ~> Image.bitmap(bitmap) ~@> fadeIn(photoFade)
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
      seekBar ~> seek((ts * seekBar.get.getMax / duration).toInt)
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
    seekBar ~> seek(0)
    stop()
    positionMap(chapter, 0, tiltZoom = true)
    positionMan(chapter, 0)
  }
}
