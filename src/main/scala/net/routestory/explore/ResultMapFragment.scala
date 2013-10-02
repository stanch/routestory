package net.routestory.explore

import net.routestory.R
import net.routestory.model.StoryResult
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps._
import com.google.android.gms.maps.model._
import net.routestory.parts.RouteStoryFragment
import org.scaloid.common._
import net.routestory.parts.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import com.google.android.gms.maps.model.CameraPosition
import scala.concurrent.Future
import scala.util.Try
import scala.async.Async.{ async, await }

class ResultMapFragment extends RouteStoryFragment with StoryListObserverFragment {
  lazy val map = findFrag[SupportMapFragment](Tag.resultsMap).get.getMap
  var markers = Map[Marker, StoryResult]()
  var routes = List[Polyline]()

  val kellyColors = List(
    "#FFB300", "#803E75", "#FF6800", "#A6BDD7",
    "#C10020", "#CEA262", "#817066", "#007D34",
    "#F6768E", "#00538A", "#FF7A5C", "#53377A",
    "#FF8E00", "#B32851", "#F4C800", "#7F180D",
    "#93AA00", "#593315", "#F13A13", "#232C16"
  )

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    f[SupportMapFragment](Id.map, Tag.resultsMap)
  }

  override def onStart() {
    super.onStart()
    map.setOnMarkerClickListener { marker: Marker ⇒
      val story = markers(marker)
      // TODO: wtf? how to measure one?
      val a = List(getView.getMeasuredWidth, getView.getMeasuredHeight).min * 0.8
      val view = ResultRow.getView(None, a.toInt, story, getActivity)
      new AlertDialog.Builder(ctx).setView(view).create().show()
      true
    }
    observe()
  }

  override def onStop() {
    super.onStop()
    neglect()
  }

  def update(data: Future[List[StoryResult]]) = async {
    val res = await(data)
    routes.foreach(_.remove())
    markers.foreach(_._1.remove())
    routes = List()
    markers = Map()
    val boundsBuilder = LatLngBounds.builder()
    res.zipWithIndex.foreach {
      case (r, i) ⇒
        val routeOptions = (new PolylineOptions).color(Color.parseColor(kellyColors(i % kellyColors.length)))
        r.geom.asLatLngs.foreach { p ⇒
          boundsBuilder.include(p)
          routeOptions.add(p)
        }
        Ui(routes ::= map.addPolyline(routeOptions))
        Ui(markers += (map.addMarker(new MarkerOptions()
          .position(r.geom.asLatLngs(0))
          .icon(BitmapDescriptorFactory.fromResource(R.drawable.flag_start))
          .anchor(0.3f, 1)) → r))
    }
    val bounds = boundsBuilder.build()
    Ui(Try {
      map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 30 dip))
    } getOrElse {
      map.setOnCameraChangeListener { p: CameraPosition ⇒
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 30 dip))
        map.setOnCameraChangeListener { p: CameraPosition ⇒ () }
      }
    })
  }
}
