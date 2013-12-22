package net.routestory.display

import net.routestory.R
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model._
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.parts.{ FragmentData, RouteStoryFragment }
import net.routestory.parts.Implicits._
import scala.concurrent.Future
import android.widget.{ Button, FrameLayout }
import ViewGroup.LayoutParams._
import scala.async.Async.{ async, await }
import scala.ref.WeakReference
import net.routestory.model.Story

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

  override def onFirstStart() {
    map.setMapType(GoogleMap.MAP_TYPE_HYBRID)

    story foreachUi { s ⇒
      mapManager.add(s.chapters(0))
      map.setOnCameraChangeListener { p: CameraPosition ⇒
        mapManager.update()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapManager.bounds.get, 30 dp))
        map.setOnCameraChangeListener { p: CameraPosition ⇒ mapManager.update() }
      }
    }
  }
}
