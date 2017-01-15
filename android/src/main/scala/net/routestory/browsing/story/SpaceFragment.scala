package net.routestory.browsing.story

import akka.actor.{ ActorLogging, Props }
import android.os.Bundle
import android.view.{ LayoutInflater, View, ViewGroup }
import com.google.android.gms.maps.model.{ f ⇒ _, _ }
import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap, SupportMapFragment }
import io.dylemma.frp.Observer
import macroid.FullDsl._
import macroid.IdGeneration
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.Ui
import net.routestory.data.Story
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.Implicits._

class SpaceFragment extends RouteStoryFragment with AkkaFragment with IdGeneration {
  lazy val actor = Some(actorSystem.actorSelection("/user/astronaut"))
  lazy val coordinator = actorSystem.actorSelection("/user/coordinator")

  lazy val mapView = this.findFrag[SupportMapFragment](Tag.overviewMap).get.get.getView
  lazy val map = this.findFrag[SupportMapFragment](Tag.overviewMap).get.get.getMap
  lazy val mapManager = new MapManager(map)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    f[SupportMapFragment].framed(Id.map, Tag.overviewMap)
  }

  override def onStart() {
    super.onStart()
    map.setMapType(GoogleMap.MAP_TYPE_HYBRID)
  }

  def positionMap(chapter: Story.Chapter) = Ui {
    map.animateCamera(CameraUpdateFactory.newCameraPosition {
      CameraPosition.builder().target(chapter.locations.head.data: LatLng).zoom(16).build()
    })
  }
}

object Astronaut {
  def props = Props(new Astronaut)
}

class Astronaut extends FragmentActor[SpaceFragment] with ActorLogging with Observer {
  import Coordinator._

  lazy val coordinator = context.actorSelection("../coordinator")

  def receive = receiveUi andThen {
    case FragmentActor.AttachUi(_) ⇒
      coordinator ! Remind

    case UpdateChapter(c) ⇒
      withUi { f ⇒
        f.mapManager.addRoute(c) ~ f.positionMap(c) ~ Ui {
          f.mapManager.cueStream.foreach(cue ⇒ coordinator ! UpdateFocus(c, cue))
        }
      }

    case UpdateTree(c, tree) ⇒
      withUi { f ⇒
        f.mapManager.removeMarkers() ~ f.mapManager.addMarkers(c, tree)
      }

    case UpdateFocus(c, focus) ⇒
      withUi { f ⇒
        f.mapManager.focus(focus)
      }

    case _ ⇒
  }
}
