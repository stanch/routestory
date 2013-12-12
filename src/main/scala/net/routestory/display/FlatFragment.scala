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
import net.routestory.parts.RouteStoryFragment
import net.routestory.parts.Implicits._
import scala.concurrent.Future
import android.widget.{ Button, FrameLayout }
import ViewGroup.LayoutParams._
import scala.async.Async.{ async, await }
import scala.ref.WeakReference

class FlatFragment extends RouteStoryFragment {
  lazy val story = getActivity.asInstanceOf[HazStory].story
  //lazy val media = getActivity.asInstanceOf[HazStory].media
  lazy val display = getActivity.getWindowManager.getDefaultDisplay
  lazy val mapView = findFrag[SupportMapFragment](Tag.overviewMap).get.getView
  lazy val map = findFrag[SupportMapFragment](Tag.overviewMap).get.getMap
  lazy val routeManager = new RouteManager(map)
  lazy val markerManager = story.map(s ⇒ new MarkerManager(map, mapView, List(display.getWidth, display.getHeight), s.chapters(0), WeakReference(getActivity)))

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
          On.click(markerManager foreachUi { mm ⇒
            mm.hideOverlays = !mm.hideOverlays
            toggleOverlays ~> text(if (mm.hideOverlays) R.string.show_overlays else R.string.hide_overlays)
            mm.update()
          })
      )
    )
  }

  override def onFirstStart() {
    map.setMapType(GoogleMap.MAP_TYPE_HYBRID)

    /* Initialize marker manager */
    async {
      val mm = await(markerManager)
      //val m = await(media)
      //await(Future.sequence(m)) // preload not to cache twice
      await(Future.sequence(mm.loadingItems))
      Ui {
        mm.update()
        map.setOnMarkerClickListener(mm.onMarkerClick _)
      }
    }

    /* Put start and end markers */
    // format: OFF
    val rm = story mapUi { s ⇒
      routeManager.add(s.chapters(0))
      List(
        routeManager.start.get → R.drawable.flag_start,
        routeManager.end.get → R.drawable.flag_end
      ) map { case (l, d) ⇒
        map.addMarker(new MarkerOptions()
          .position(l).anchor(0.3f, 1)
          .icon(BitmapDescriptorFactory.fromResource(d)))
      }
      ()
    }
    // format: ON

    /* Move map to bounds */
    map.setOnCameraChangeListener { p: CameraPosition ⇒
      async {
        val mm = await(markerManager)
        await(rm)
        Ui {
          map.moveCamera(CameraUpdateFactory.newLatLngBounds(routeManager.bounds.get, 30 dp))
          map.setOnCameraChangeListener { p: CameraPosition ⇒ mm.update() }
        }
      }
    }
  }
}
