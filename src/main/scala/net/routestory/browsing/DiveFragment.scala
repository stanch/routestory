package net.routestory.browsing

import akka.actor.{ ActorLogging, Props }
import android.media.MediaPlayer
import android.os.{ Bundle, Handler, SystemClock }
import android.view.ViewGroup.LayoutParams._
import android.view.{ Gravity, LayoutInflater, View, ViewGroup }
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget._
import com.google.android.gms.maps.model.{ f ⇒ _, _ }
import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap, SupportMapFragment }
import com.javadocmd.simplelatlng.LatLngTool
import macroid.FullDsl._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.util.Ui
import macroid.{ IdGeneration, Tweak }
import net.routestory.R
import net.routestory.data.Story
import net.routestory.data.Story.Chapter
import net.routestory.maps.RouteMapManager
import net.routestory.ui.RouteStoryFragment
import net.routestory.ui.Styles._
import net.routestory.util.Implicits._
import rx.Var

import scala.concurrent.ExecutionContext.Implicits.global

object DiveFragment {
  val photoDuration = 1500
  val photoFade = 300
}

class DiveFragment extends RouteStoryFragment with AkkaFragment with IdGeneration {

  lazy val actor = Some(actorSystem.actorSelection("/user/diver"))
  lazy val coordinator = actorSystem.actorSelection("/user/coordinator")

  lazy val map = this.findFrag[SupportMapFragment](Tag.previewMap).get.get.getMap
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

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[VerticalLinearLayout](
      l[FrameLayout](
        l[FrameLayout](
          f[SupportMapFragment].framed(Id.map, Tag.previewMap)
        ),
        l[FrameLayout](
          w[ImageView] <~ wire(imageView)
        ),
        l[FrameLayout](
          w[Button] <~
            wire(playBig) <~
            lp[FrameLayout](80 dp, 80 dp, Gravity.CENTER) <~
            Bg.res(R.drawable.play_big)
        )
      ) <~ lp[LinearLayout](WRAP_CONTENT, WRAP_CONTENT, 1.0f),
      l[HorizontalLinearLayout](
        w[Button] <~
          Bg.res(R.drawable.play) <~
          lp[LinearLayout](32 dp, 32 dp) <~
          wire(play),
        w[Button] <~
          Bg.res(R.drawable.pause) <~
          lp[LinearLayout](32 dp, 32 dp) <~
          wire(pause) <~
          hide,
        w[SeekBar] <~
          wire(seekBar) <~
          lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT)
      ) <~ padding(left = 16 dp, right = 8 dp, top = 8 dp, bottom = 8 dp)
    ) <~ wire(layout)
  }

  override def onStart() = {
    super.onStart()
    map.setMapType(GoogleMap.MAP_TYPE_NORMAL)
  }

  override def onStop() = {
    super.onStop()
    handler.removeCallbacksAndMessages(null)
  }

  def prepareControls(chapter: Chapter) = Ui.sequence(
    seekBar <~ Tweak[SeekBar] { bar ⇒
      bar.setMax(bar.getWidth)
      bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener {
        def onProgressChanged(bar: SeekBar, position: Int, fromUser: Boolean) {
          cue.update(1.0 * position / bar.getMax)
          if (fromUser) {
            //coordinator ! Coordinator.Cue(cue.now * chapter.duration, source)
          }
        }
        def onStopTrackingTouch(bar: SeekBar) = if (playing.now) getUi(start(chapter))
        def onStartTrackingTouch(bar: SeekBar) = getUi(stop)
      })
    },
    List(play, playBig) <~ On.click(Ui(playing.update(true)) ~ start(chapter)),
    pause <~ On.click(Ui(playing.update(false)) ~ stop)
  )

  def populateMap(chapter: Chapter) = Ui.sequence(
    positionMap(chapter, 0, tiltZoom = true),
    Ui(mapManager.add(chapter)),
    Ui(mapManager.updateMan(mapManager.start.get))
  )

  def positionMap(chapter: Chapter, ts: Long, animate: Boolean = true, tiltZoom: Boolean = false) = Ui {
    val now = chapter.locationAt(ts * ratio.now)
    val bearing = List(3800, 4200).map(t ⇒ LatLngTool.initialBearing(now, chapter.locationAt((ts + t) * ratio.now)).toFloat).sum / 2
    val position = CameraUpdateFactory.newCameraPosition(if (tiltZoom) {
      CameraPosition.builder().target(now).tilt(90).zoom(19).bearing(bearing).build()
    } else {
      CameraPosition.builder(map.getCameraPosition).target(now).bearing(bearing).build()
    })
    if (animate) map.animateCamera(position) else map.moveCamera(position)
  }

  def positionMan(chapter: Chapter, ts: Long) = Ui {
    val now = chapter.locationAt(ts * ratio.now)
    mapManager.updateMan(now)
  }

  def stop = Ui.sequence(
    pause <~ hide,
    play <~ show,
    imageView <~ hide,
    layout <~ unImmerse,
    Ui(handler.removeCallbacksAndMessages(null))
  )

  def start(chapter: Chapter) = Ui {
    runUi(
      playBig <~ hide,
      play <~ hide,
      pause <~ show,
      layout <~ immerse
    )

    val duration = Math.ceil(chapter.duration / ratio.now).toInt
    val from = Math.ceil(cue.now * duration)
    val start = SystemClock.uptimeMillis

    def move: Ui[Any] = {
      val ts = SystemClock.uptimeMillis - start + from.toInt
      Ui.sequence(
        positionMap(chapter, ts),
        Ui(handler.postDelayed(move, 500))
      )
    }
    handler.postAtTime(move, start)

    def walk: Ui[Any] = {
      val ts = SystemClock.uptimeMillis - start + from.toInt
      Ui.sequence(
        seekBar <~ seek((ts * seekBar.get.getMax / duration).toInt),
        positionMan(chapter, ts),
        Ui(if (ts <= duration) handler.postDelayed(walk, 100) else handler.postDelayed(rewind(chapter), 1000))
      )
    }
    handler.postAtTime(walk, start)
  }

  def rewind(chapter: Chapter) = Ui.sequence(
    playBig <~ show,
    play <~ show,
    pause <~ hide,
    seekBar <~ seek(0),
    Ui(playing.update(false)),
    stop,
    positionMap(chapter, 0, tiltZoom = true),
    positionMan(chapter, 0)
  )
}

object Diver {
  def props = Props(new Diver)
}

class Diver extends FragmentActor[DiveFragment] with ActorLogging {
  import net.routestory.browsing.Coordinator._

  lazy val coordinator = context.actorSelection("../coordinator")

  def receive = receiveUi andThen {
    case FragmentActor.AttachUi(_) ⇒
      coordinator ! Remind

    case UpdateChapter(c) ⇒
      withUi(f ⇒ f.populateMap(c) ~ f.prepareControls(c))

    //    case Cue(t, s) if s != Cue.Diver ⇒
    //      chapter foreach { c ⇒
    //        withUi(f ⇒ Ui.sequence(
    //          f.seekBar <~ seek((t * f.seekBar.get.getMax / c.duration).toInt),
    //          f.positionMap(c, Math.ceil(t / f.ratio.now).toInt, animate = false),
    //          f.positionMan(c, Math.ceil(t / f.ratio.now).toInt)
    //        ))
    //      }

    case _ ⇒
  }
}
