package net.routestory.browsing

import net.routestory.data.Story
import net.routestory.maps.FlatMapManager

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import android.os.Bundle
import android.view.{ Gravity, LayoutInflater, View, ViewGroup }
import android.view.ViewGroup.LayoutParams._
import android.widget.{ Button, FrameLayout }

import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap, SupportMapFragment }
import com.google.android.gms.maps.model.{ f ⇒ _, _ }
import macroid.FullDsl._

import net.routestory.R
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.Implicits._
import macroid.IdGeneration
import macroid.util.Ui
import macroid.akkafragments.{ FragmentActor, AkkaFragment }
import akka.actor.{ ActorLogging, Props }

class SpaceFragment extends RouteStoryFragment with AkkaFragment with IdGeneration {
  lazy val actor = Some(actorSystem.actorSelection("/user/astronaut"))
  lazy val coordinator = actorSystem.actorSelection("/user/coordinator")

  lazy val mapView = this.findFrag[SupportMapFragment](Tag.overviewMap).get.get.getView
  lazy val map = this.findFrag[SupportMapFragment](Tag.overviewMap).get.get.getMap
  lazy val mapManager = new FlatMapManager(map, mapView, displaySize, coordinator)

  var toggleOverlays = slot[Button]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[FrameLayout](
      l[FrameLayout](
        f[SupportMapFragment].framed(Id.map, Tag.overviewMap)
      ),
      l[FrameLayout](
        w[Button] <~
          text(R.string.hide_overlays) <~
          wire(toggleOverlays) <~
          lp[FrameLayout](WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL) <~
          On.click(Ui.sequence(
            Ui(mapManager.hideOverlays = !mapManager.hideOverlays),
            toggleOverlays <~ text(if (mapManager.hideOverlays) R.string.show_overlays else R.string.hide_overlays),
            mapManager.update
          ))
      )
    )
  }

  override def onStart() {
    super.onStart()
    map.setMapType(GoogleMap.MAP_TYPE_HYBRID)
  }

  def populateMap(chapter: Story.Chapter) = Ui {
    mapManager.add(chapter) foreachUi { _ ⇒
      mapManager.update.run
      map.onCameraChange { _ ⇒
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapManager.bounds.get, 30 dp))
        map.onCameraChange(_ ⇒ mapManager.update.run)
      }
    }
  }

  def positionMap(chapter: Story.Chapter, cue: Double, animate: Boolean = true) = Ui {
    val now = chapter.locationAt(cue)
    val position = CameraUpdateFactory.newCameraPosition {
      CameraPosition.builder(map.getCameraPosition).target(now).build()
    }
    if (animate) map.animateCamera(position) else map.moveCamera(position)
  }
}

object Astronaut {
  def props = Props(new Astronaut)
}

class Astronaut extends FragmentActor[SpaceFragment] with ActorLogging {
  import Coordinator._

  lazy val coordinator = context.actorSelection("../coordinator")

  def receive = receiveUi andThen {
    case FragmentActor.AttachUi(_) ⇒
      coordinator ! Remind

    case UpdateChapter(c) ⇒
      withUi(f ⇒ f.populateMap(c))

    //    case Cue(t, s) if s != Cue.Astronaut ⇒
    //      chapter foreach { c ⇒
    //        withUi(f ⇒ f.positionMap(c, t, animate = false))
    //      }

    case _ ⇒
  }
}
