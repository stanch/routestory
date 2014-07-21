package net.routestory.browsing.story

import akka.actor.{ ActorLogging, Props }
import android.os.{ Bundle, Handler, SystemClock }
import android.view.ViewGroup.LayoutParams._
import android.view.{ Gravity, LayoutInflater, View, ViewGroup }
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget._
import com.google.android.gms.maps.model.{ f ⇒ _, _ }
import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap, SupportMapFragment }
import com.javadocmd.simplelatlng.LatLngTool
import io.dylemma.frp.Observer
import macroid.FullDsl._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.contrib.{ SeekTweaks, LpTweaks, BgTweaks }
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.Ui
import macroid.{ IdGeneration, Tweak }
import net.routestory.R
import net.routestory.data.Clustering
import net.routestory.data.Story.Chapter
import net.routestory.ui.RouteStoryFragment
import net.routestory.ui.Styles._
import net.routestory.util.Implicits._
import rx.{ Obs, Rx, Var }

object DiveFragment {
  val photoDuration = 1500
  val photoFade = 300
}

class DiveFragment extends RouteStoryFragment with AkkaFragment with IdGeneration {
  lazy val actor = Some(actorSystem.actorSelection("/user/diver"))
  lazy val coordinator = actorSystem.actorSelection("/user/coordinator")
  lazy val previewer = actorSystem.actorSelection("/user/previewer")

  lazy val map = this.findFrag[SupportMapFragment](Tag.previewMap).get.get.getMap
  lazy val mapManager = new MapManager(map, iconAlpha = 0.7f, centerIcons = false)
  lazy val handler = new Handler

  // playback vars
  val cue = Var(0.0)
  var cueObs: Option[Obs] = None
  val mapCue = Var(0.0)
  var mapCueObs: Option[Obs] = None
  var lastFocus = 0

  // widgets
  var layout = slot[LinearLayout]
  var playBig = slot[Button]
  var play = slot[Button]
  var pause = slot[Button]
  var seekBar = slot[SeekBar]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[VerticalLinearLayout](
      l[FrameLayout](
        f[SupportMapFragment].framed(Id.map, Tag.previewMap),
        w[Button] <~
          wire(playBig) <~
          lp[FrameLayout](80 dp, 80 dp, Gravity.CENTER) <~
          BgTweaks.res(R.drawable.play_big)
      ) <~ lp[LinearLayout](WRAP_CONTENT, 0, 2.0f),
      f[PreviewFragment].framed(Id.preview, Tag.preview) <~
        lp[LinearLayout](WRAP_CONTENT, 0, 1.0f),
      l[HorizontalLinearLayout](
        w[Button] <~
          BgTweaks.res(R.drawable.play) <~
          lp[LinearLayout](32 dp, 32 dp) <~
          wire(play),
        w[Button] <~
          BgTweaks.res(R.drawable.pause) <~
          lp[LinearLayout](32 dp, 32 dp) <~
          wire(pause) <~
          hide,
        w[SeekBar] <~
          wire(seekBar) <~
          LpTweaks.matchWidth
      ) <~ padding(left = 16 dp, right = 8 dp, top = 8 dp, bottom = 8 dp) <~
        BgTweaks.res(R.color.dark)
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
          if (fromUser) {
            val pos = 1.0 * position / bar.getMax
            cue() = pos
            mapCue() = pos
          }
        }
        def onStopTrackingTouch(bar: SeekBar) = ()
        def onStartTrackingTouch(bar: SeekBar) = ()
      })
    },
    List(play, playBig) <~ On.click(start(chapter)),
    pause <~ On.click(stop)
  )

  def positionMap(chapter: Chapter, timestamp: Double, animate: Boolean, tiltZoom: Boolean) = Ui {
    val now = chapter.locationAt(timestamp)
    val bearing = LatLngTool.initialBearing(now, chapter.locationAt(timestamp + 20)).toFloat
    val position = CameraUpdateFactory.newCameraPosition(if (tiltZoom) {
      CameraPosition.builder().target(now).tilt(90).zoom(19).bearing(bearing).build()
    } else {
      CameraPosition.builder(map.getCameraPosition).target(now).bearing(bearing).build()
    })
    if (animate) map.animateCamera(position) else map.moveCamera(position)
  }

  def positionMan(chapter: Chapter, timestamp: Double) = {
    val now = chapter.locationAt(timestamp)
    mapManager.addMan(now)
  }

  def hook(chapter: Chapter) = Ui {
    cueObs = Some(cue foreach { c ⇒
      val timestamp = c * chapter.duration
      val focus = chapter.knownElements.indexWhere(_.timestamp >= timestamp)
      if (focus != lastFocus) {
        coordinator ! Coordinator.UpdateFocus(chapter, focus)
        lastFocus = focus
      }
      runUi(
        positionMan(chapter, timestamp),
        seekBar <~ SeekTweaks.seek((c * seekBar.get.getMax).toInt)
      )
    })
    mapCueObs = Some(mapCue foreach { c ⇒
      val timestamp = c * chapter.duration
      runUi(
        positionMap(chapter, timestamp, animate = true, tiltZoom = c == 0)
      )
    })
  }

  def walk(chapter: Chapter): Ui[Any] = Ui {
    val freq = 100
    // 10 seconds every second
    cue() = cue() + 10 * freq / 1000.0 / chapter.duration
    if (cue() >= 1) handler.postDelayed(rewind(chapter), 1000) else handler.postDelayed(walk(chapter), freq)
  }

  def move(chapter: Chapter): Ui[Any] = Ui {
    val freq = 400
    // 10 seconds every second
    mapCue() = mapCue() + 10 * freq / 1000.0 / chapter.duration
    handler.postDelayed(move(chapter), freq)
  }

  def start(chapter: Chapter) = Ui.sequence(
    playBig <~ hide,
    play <~ hide,
    pause <~ show,
    layout <~ immerse,
    walk(chapter),
    move(chapter)
  )

  def stop = Ui.sequence(
    pause <~ hide,
    play <~ show,
    layout <~ unImmerse,
    Ui(handler.removeCallbacksAndMessages(null))
  )

  def rewind(chapter: Chapter) = Ui.sequence(
    playBig <~ show,
    Ui(cue.update(0)),
    Ui(mapCue.update(0)),
    stop
  )

  def focus(chapter: Chapter, focus: Int) = Ui {
    if (lastFocus != focus) {
      Clustering.indexLookup(mapManager.currentTrees, focus).foreach { t ⇒
        val c = mapManager.currentTrees(t).timestamp / chapter.duration
        cue() = c
        mapCue() = c
      }
    }
  }
}

object Diver {
  def props = Props(new Diver)
}

class Diver extends FragmentActor[DiveFragment] with ActorLogging with Observer {
  import Coordinator._

  lazy val coordinator = context.actorSelection("../coordinator")
  lazy val previewer = context.actorSelection("../previewer")

  def receive = receiveUi andThen {
    case FragmentActor.AttachUi(_) ⇒
      coordinator ! Remind

    case UpdateChapter(c) ⇒
      withUi { f ⇒
        f.mapManager.addRoute(c) ~
          f.hook(c) ~
          f.prepareControls(c) ~ Ui {
            f.mapManager.cueStream.foreach(cue ⇒ coordinator ! UpdateFocus(c, cue))
            f.mapManager.scaleStream.foreach { scale ⇒
              previewer ! Previewer.UpdateScale(scale)
              // slight cheating, but we are inside Ui, so should be fine
              previewer ! UpdateFocus(c, f.lastFocus)
            }
          }
      }

    case UpdateTree(c, tree) ⇒
      withUi(f ⇒ f.mapManager.removeMarkers() ~ f.mapManager.addMarkers(c, tree))

    case UpdateFocus(c, focus) ⇒
      withUi(f ⇒ f.focus(c, focus))

    case _ ⇒
  }
}
