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
  lazy val display = getActivity.getWindowManager.getDefaultDisplay
  lazy val mapView = findFrag[SupportMapFragment](Tag.overviewMap).get.getView
  lazy val map = findFrag[SupportMapFragment](Tag.overviewMap).get.getMap
  lazy val markerManager = new FlatMapManager(map, mapView, List(display.getWidth, display.getHeight), WeakReference(getActivity))

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
            markerManager.hideOverlays = !markerManager.hideOverlays
            toggleOverlays ~> text(if (markerManager.hideOverlays) R.string.show_overlays else R.string.hide_overlays)
            markerManager.update()
          }
      )
    )
  }

  override def onFirstStart() {
    map.setMapType(GoogleMap.MAP_TYPE_HYBRID)

    story foreachUi { s ⇒
      markerManager.add(s.chapters(0))
      map.setOnMarkerClickListener(markerManager.onMarkerClick)
      map.setOnCameraChangeListener { p: CameraPosition ⇒
        markerManager.update()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(markerManager.bounds.get, 30 dp))
        map.setOnCameraChangeListener { p: CameraPosition ⇒ markerManager.update() }
      }
    }
  }
}
