package net.routestory.explore

import scala.async.Async.{ async, await }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import android.app.AlertDialog
import android.os.Bundle
import android.view.{ LayoutInflater, View, ViewGroup }
import com.google.android.gms.maps._
import com.google.android.gms.maps.model.{ f ⇒ _, _ }

import org.macroid.FullDsl._

import net.routestory.model.StoryPreview
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.Implicits._
import org.macroid.IdGeneration
import android.support.v4.app.Fragment
import net.routestory.disp.StoryPreviewViewable

class StoriesMapFragment extends RouteStoryFragment with StoriesObserverFragment with IdGeneration {
  lazy val map = findFrag[SupportMapFragment](Tag.resultsMap).get.getMap
  var markers = Map[Marker, StoryPreview]()
  var routes = List[Polyline]()

  val kellyColors = List(
    "#FFB300", "#803E75", "#FF6800", "#A6BDD7",
    "#C10020", "#CEA262", "#817066", "#007D34",
    "#F6768E", "#00538A", "#FF7A5C", "#53377A",
    "#FF8E00", "#B32851", "#F4C800", "#7F180D",
    "#93AA00", "#593315", "#F13A13", "#232C16"
  )

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    f[SupportMapFragment].framed(Id.map, Tag.resultsMap)
  }

  override def onStart() {
    super.onStart()
    map.onMarkerClick { marker: Marker ⇒
      val story = markers(marker)
      // TODO: wtf? how to measure one?
      val a = List(getView.getMeasuredWidth, getView.getMeasuredHeight).min * 0.8
      val view = new StoryPreviewViewable(a.toInt).layout(story)
      new AlertDialog.Builder(getActivity).setView(view).create().show()
      true
    }
    observe()
  }

  override def onStop() {
    super.onStop()
    neglect()
  }

  def update(data: Future[List[StoryPreview]]) = async {
    val res = await(data)
    routes.foreach(_.remove())
    markers.foreach(_._1.remove())
    routes = List()
    markers = Map()
    //    val boundsBuilder = LatLngBounds.builder()
    //    res.zipWithIndex.foreach {
    //      case (r, i) ⇒
    //        val routeOptions = (new PolylineOptions).color(Color.parseColor(kellyColors(i % kellyColors.length)))
    //        r.geom.asLatLngs.foreach { p ⇒
    //          boundsBuilder.include(p)
    //          routeOptions.add(p)
    //        }
    //        Ui(routes ::= map.addPolyline(routeOptions))
    //        Ui(markers += (map.addMarker(new MarkerOptions()
    //          .position(r.geom.asLatLngs(0))
    //          .icon(BitmapDescriptorFactory.fromResource(R.drawable.flag_start))
    //          .anchor(0.3f, 1)) → r))
    //    }
    //    val bounds = boundsBuilder.build()
    //    Ui(Try {
    //      map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 30 dp))
    //    } getOrElse {
    //      map.setOnCameraChangeListener { p: CameraPosition ⇒
    //        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 30 dp))
    //        map.setOnCameraChangeListener { p: CameraPosition ⇒ () }
    //      }
    //    })
  }
}
