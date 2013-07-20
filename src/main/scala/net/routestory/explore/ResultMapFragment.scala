package net.routestory.explore;

import net.routestory.R
import net.routestory.model.StoryResult
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import com.google.android.gms.maps._
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.VisibleRegion
import net.routestory.parts.StoryFragment
import org.scaloid.common._
import java.util.Locale
import net.routestory.parts.Implicits._
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import com.google.android.gms.maps.model.CameraPosition
import scala.concurrent.Future
import scala.util.Try
import rx._
import android.widget.FrameLayout.LayoutParams

class ResultMapFragment extends StoryFragment {
    lazy val mMap = findFrag[MapFragment]("results_map").getMap
    var mMarkers = Map[Marker, StoryResult]()
    var mRoutes = List[Polyline]()

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
        new FrameLayout(ctx) {
            this += new FrameLayout(ctx) {
                this += fragment(MapFragment.newInstance(), 1, "results_map")
            }
            this += new FrameLayout(ctx) {
                this += new Button(ctx) {
                    setText(R.string.search_this_area)
                    setLayoutParams(new LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
                    ))
                    setOnClickListener { v: View ⇒
                        val r = mMap.getProjection.getVisibleRegion
                        getActivity.asInstanceOf[SearchResultsActivity].geoQuery("%f,%f,%f,%f".formatLocal(Locale.US, r.nearLeft.latitude, r.nearLeft.longitude, r.farRight.latitude, r.farRight.longitude));
                    }
                }
            }
        }
    }

    override def onStart() {
        super.onStart()
        mMap.setOnMarkerClickListener { marker: Marker ⇒
            val story = mMarkers(marker)
            // TODO: wtf? how to measure one?
            val a = List(getView.getMeasuredWidth, getView.getMeasuredHeight).min * 0.8
            new AlertDialog.Builder(ctx).setView(ResultRow.getView(null, a.toInt, story, getActivity)).create().show()
            true
        }
        val stories = getActivity.asInstanceOf[HazStories].getStories
        Obs(stories) {
            update(stories())
        }
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
                    r.geometry.coordinates map { l ⇒
                        new LatLng(l(0), l(1))
                    } foreach { p ⇒
                        boundsBuilder.include(p)
                        routeOptions.add(p)
                    }
                    routeOptions.color(Color.parseColor(kellyColors(i % kellyColors.length)))
                    mRoutes ::= mMap.addPolyline(routeOptions)
                    val start = r.geometry.coordinates.take(1).map(l ⇒ new LatLng(l(0), l(1))).toList(0)
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
