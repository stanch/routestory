package net.routestory.browsing

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import android.os.Bundle
import android.view.{ Gravity, LayoutInflater, View, ViewGroup }
import android.view.ViewGroup.LayoutParams._
import android.widget.{ Button, FrameLayout }

import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap, SupportMapFragment }
import macroid.FullDsl._

import net.routestory.R
import net.routestory.model.Story
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.FragmentData
import net.routestory.util.Implicits._
import macroid.IdGeneration
import net.routestory.display.FlatMapManager
import macroid.util.Ui

class StoryFlatFragment extends RouteStoryFragment with FragmentData[Future[Story]] with IdGeneration {
  lazy val story = getFragmentData
  lazy val mapView = this.findFrag[SupportMapFragment](Tag.overviewMap).get.get.getView
  lazy val map = this.findFrag[SupportMapFragment](Tag.overviewMap).get.get.getMap
  lazy val mapManager = new FlatMapManager(map, mapView, displaySize)

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

  var positioned = false
  override def onStart() {
    super.onStart()
    if (!positioned) positioned = true else return

    map.setMapType(GoogleMap.MAP_TYPE_HYBRID)
    story foreachUi { s ⇒
      mapManager.add(s.chapters(0))
      map.onCameraChange { _ ⇒
        mapManager.update.run
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapManager.bounds.get, 30 dp))
        map.onCameraChange(_ ⇒ mapManager.update.run)
      }
    }
  }
}
