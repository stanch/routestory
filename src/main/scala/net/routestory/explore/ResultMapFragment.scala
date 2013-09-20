package net.routestory.explore

import net.routestory.R
import net.routestory.model.StoryResult
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import com.google.android.gms.maps._
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import net.routestory.parts.{ FragmentData, StoryFragment }
import org.scaloid.common._
import net.routestory.parts.Implicits._
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import com.google.android.gms.maps.model.CameraPosition
import scala.concurrent.Future
import scala.util.Try
import rx._
import net.routestory.parts.Tweaks._

class ResultMapFragment extends StoryFragment with FragmentData[HazStories] {
  lazy val mMap = findFrag[SupportMapFragment](Tag.resultsMap).getMap
  var mMarkers = Map[Marker, StoryResult]()
  var mRoutes = List[Polyline]()

  lazy val storyteller = getFragmentData
  lazy val stories = storyteller.getStories
  var observer: Obs = _

  val kellyColors = List(
    "#FFB300",
    "#803E75",
    "#FF6800",
    "#A6BDD7",
    "#C10020",
    "#CEA262",
    "#817066",
    "#007D34",
    "#F6768E",
    "#00538A",
    "#FF7A5C",
    "#53377A",
    "#FF8E00",
    "#B32851",
    "#F4C800",
    "#7F180D",
    "#93AA00",
    "#593315",
    "#F13A13",
    "#232C16")

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[FrameLayout](
      l[FrameLayout](
        fragment(SupportMapFragment.newInstance(), Id.map, Tag.resultsMap)
      )
    )
    //      this += new FrameLayout(ctx) {
    //        this += new Button(ctx) {
    //          setText(R.string.search_this_area)
    //          setLayoutParams(new LayoutParams(
    //            WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
    //          ))
    //          setOnClickListener { v: View ⇒
    //            val r = mMap.getProjection.getVisibleRegion
    //            getActivity.asInstanceOf[SearchResultsActivity].geoQuery("%f,%f,%f,%f".formatLocal(Locale.US, r.nearLeft.latitude, r.nearLeft.longitude, r.farRight.latitude, r.farRight.longitude));
    //          }
    //        }
    //      }
  }

  override def onStart() {
    super.onStart()
    mMap.setOnMarkerClickListener { marker: Marker ⇒
      val story = mMarkers(marker)
      // TODO: wtf? how to measure one?
      val a = List(getView.getMeasuredWidth, getView.getMeasuredHeight).min * 0.8
      val view = ResultRow.getView(None, a.toInt, story, getActivity) ~> p8dding
      new AlertDialog.Builder(ctx).setView(view).create().show()
      true
    }
    observer = Obs(stories) {
      update(stories())
    }
  }

  override def onDestroyView() {
    super.onDestroyView()
    observer.active = false
  }

  def update(res: Future[List[StoryResult]]) {
    res.onSuccessUi {
      case results ⇒
        mRoutes.foreach(_.remove())
        mMarkers.foreach(_._1.remove())
        mRoutes = List()
        mMarkers = Map()
        val boundsBuilder = LatLngBounds.builder()
        for (i ← results.indices) {
          val r = results(i)
          val routeOptions = new PolylineOptions()
          r.geom.coordinates map { l ⇒
            new LatLng(l(0), l(1))
          } foreach { p ⇒
            boundsBuilder.include(p)
            routeOptions.add(p)
          }
          routeOptions.color(Color.parseColor(kellyColors(i % kellyColors.length)))
          mRoutes ::= mMap.addPolyline(routeOptions)
          val start = r.geom.coordinates.take(1).map(l ⇒ new LatLng(l(0), l(1))).toList(0)
          mMarkers += (mMap.addMarker(new MarkerOptions()
            .position(start)
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.flag_start))
            .anchor(0.3f, 1)) → r)
        }
        val bounds = boundsBuilder.build()
        Try {
          mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 30 dip))
        } getOrElse {
          mMap.setOnCameraChangeListener { p: CameraPosition ⇒
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 30 dip))
            mMap.setOnCameraChangeListener { p: CameraPosition ⇒ () }
          }
        }
    }
  }
}
