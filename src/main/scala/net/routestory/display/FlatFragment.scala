package net.routestory.display

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.ref.WeakReference

import android.os.Bundle
import android.view.{ Gravity, LayoutInflater, View, ViewGroup }
import android.view.ViewGroup.LayoutParams._
import android.widget.{ Button, FrameLayout }

import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap, SupportMapFragment }

import net.routestory.R
import net.routestory.model.Story
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.FragmentData
import net.routestory.util.Implicits._

class FlatFragment extends RouteStoryFragment with FragmentData[Future[Story]] {
  lazy val story = getFragmentData
  lazy val mapView = findFrag[SupportMapFragment](Tag.overviewMap).get.getView
  lazy val map = findFrag[SupportMapFragment](Tag.overviewMap).get.getMap
  lazy val mapManager = new FlatMapManager(map, mapView, displaySize, WeakReference(getActivity))

  var toggleOverlays = slot[Button]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[FrameLayout](
      l[FrameLayout](
        f[SupportMapFragment].framed(Id.map, Tag.overviewMap)
      ),
      l[FrameLayout](
        w[Button] ~>
          text(R.string.hide_overlays) ~>
          wire(toggleOverlays) ~>
          lp(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL) ~>
          On.click {
            mapManager.hideOverlays = !mapManager.hideOverlays
            toggleOverlays ~> text(if (mapManager.hideOverlays) R.string.show_overlays else R.string.hide_overlays)
            mapManager.update()
          }
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
        mapManager.update()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapManager.bounds.get, 30 dp))
        map.onCameraChange(_ ⇒ mapManager.update())
      }
    }
  }
}
