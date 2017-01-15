package net.routestory.browsing.story

import akka.actor.{ ActorLogging, Props }
import android.content.res.Configuration
import android.graphics.Color
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
import macroid.{ Transformer, Ui, IdGeneration, Tweak }
import net.routestory.R
import net.routestory.data.{ Story, Timed, Pruning, Clustering }
import net.routestory.data.Story.Chapter
import net.routestory.ui.{ Styles, RouteStoryFragment }
import net.routestory.ui.Styles._
import net.routestory.util.Implicits._
import rx.{ Obs, Rx, Var }

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
  var seeking = false

  // widgets
  var layoutSlot = slot[LinearLayout]
  var mapSlot = slot[FrameLayout]
  var previewSlot = slot[FrameLayout]

  var playBig = slot[Button]
  var play = slot[Button]
  var pause = slot[Button]
  var seekBar = slot[SeekBar]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val mapLayout =
      l[FrameLayout](
        f[SupportMapFragment].framed(Id.map, Tag.previewMap),
        w[Button] <~
          wire(playBig) <~
          lp[FrameLayout](80 dp, 80 dp, Gravity.CENTER) <~
          BgTweaks.res(R.drawable.play_big)
      ) <~ mapSlotParams <~ wire(mapSlot)

    val previewLayout =
      f[PreviewFragment].framed(Id.preview, Tag.preview) <~
        previewSlotParams <~
        wire(previewSlot)

    val controlsLayout =
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
        BgTweaks.color(Color.BLACK)

    val layout =
      l[LinearLayout](mapLayout, previewLayout) <~
        lp[LinearLayout](WRAP_CONTENT, 0, 1.0f) <~
        layoutSlotParams <~
        wire(layoutSlot)

    val fullLayout = l[VerticalLinearLayout](layout, controlsLayout)
    getUi(fullLayout <~ Styles.lowProfile)
  }

  def mapSlotParams = landscape ?
    lp[LinearLayout](0, WRAP_CONTENT, 1.0f) |
    lp[LinearLayout](WRAP_CONTENT, 0, 2.0f)

  def previewSlotParams = landscape ?
    lp[LinearLayout](0, WRAP_CONTENT, 1.0f) |
    lp[LinearLayout](WRAP_CONTENT, 0, 1.0f)

  def layoutSlotParams = landscape ?
    horizontal |
    vertical

  override def onConfigurationChanged(newConfig: Configuration) = {
    super.onConfigurationChanged(newConfig)
    runUi(
      mapSlot <~ mapSlotParams,
      previewSlot <~ previewSlotParams,
      layoutSlot <~ layoutSlotParams
    )
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
        def onProgressChanged(bar: SeekBar, position: Int, fromUser: Boolean) = {
          if (fromUser) {
            val pos = 1.0 * position / bar.getMax
            // TODO: wtf
            seeking = true
            mapCue() = pos
            seeking = false
          }
        }
        def onStartTrackingTouch(bar: SeekBar) = ()
        def onStopTrackingTouch(bar: SeekBar) = {
          val pos = 1.0 * bar.getProgress / bar.getMax
          cue() = pos
          mapCue() = pos
        }
      })
    },
    List(play, playBig) <~ On.click(start(chapter)),
    pause <~ On.click(stop)
  )

  def positionMap(chapter: Chapter, route: Story.Route, timestamp: Double, animate: Boolean, tiltZoom: Boolean) = Ui {
    val now = chapter.locationAt(timestamp)
    val bearing = LatLngTool.initialBearing(
      chapter.locationAt(timestamp, route),
      chapter.locationAt(timestamp + 10, route)
    ).toFloat
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
    val prunedRoute = Pruning.pruneLocations(chapter.locations, 0.000001)
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
        if (seeking) positionMan(chapter, timestamp) else Ui.nop,
        positionMap(chapter, prunedRoute, timestamp, animate = !seeking, tiltZoom = c == 0)
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
    layoutSlot <~ immerse,
    walk(chapter),
    move(chapter)
  )

  def stop = Ui.sequence(
    pause <~ hide,
    play <~ show,
    layoutSlot <~ unImmerse,
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
