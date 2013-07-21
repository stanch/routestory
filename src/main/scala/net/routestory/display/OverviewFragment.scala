package net.routestory.display

import net.routestory.R
import android.app.ProgressDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.model._
import net.routestory.parts.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scaloid.common._
import net.routestory.parts.StoryFragment
import net.routestory.parts.Implicits._
import akka.dataflow._
import scala.concurrent.Future
import android.widget.{ Button, FrameLayout }
import android.widget.FrameLayout.LayoutParams

class OverviewFragment extends StoryFragment {
  lazy val display = getActivity.getWindowManager.getDefaultDisplay
  lazy val mMap = findFrag[MapFragment]("overview_map").getMap
  lazy val mStory = getActivity.asInstanceOf[HazStory].getStory
  lazy val mRouteManager = flow {
    val story = await(mStory)
    val rm = new RouteManager(mMap, story)
    runOnUiThread(rm.init())
    rm
  }
  lazy val mMarkerManager = flow {
    val story = await(mStory)
    new MarkerManager(mMap, List(display.getWidth(), display.getHeight()), story)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    new FrameLayout(ctx) {
      this += new FrameLayout(ctx) {
        this += fragment(MapFragment.newInstance(), 1, "overview_map")
      }
      this += new FrameLayout(ctx) {
        this += new Button(ctx) { self ⇒
          setText(R.string.hide_overlays)
          setLayoutParams(new LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
          ))
          setOnClickListener { v: View ⇒
            mMarkerManager onSuccessUi {
              case mm ⇒
                mm.hide_overlays = !mm.hide_overlays
                self.setText(if (mm.hide_overlays) R.string.show_overlays else R.string.hide_overlays)
                mm.update()
            }
          }
        }
      }
    }
  }

  override def onFirstStart() {
    mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID)

    /* Initialize marker manager */
    flow {
      val mm = await(mMarkerManager)
      switchToUiThread()
      val progress = new ProgressDialog(ctx) {
        setMessage("Loading data...")
        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        setMax(mm.loadingItems.length)
        show()
      }
      mm.loadingItems.foreach(_.onSuccessUi {
        case _ ⇒
          progress.incrementProgressBy(1)
      })
      Future.sequence(mm.loadingItems).onSuccessUi {
        case _ ⇒
          progress.dismiss()
      }
      switchToUiThread()
      mm.update()
      mMap.setOnMarkerClickListener(mm.onMarkerClick _)
    }

    /* Put start and end markers */
    flow {
      val rm = await(mRouteManager)
      List(rm.getStart → R.drawable.flag_start, rm.getEnd → R.drawable.flag_end) map {
        case (l, d) ⇒
          mMap.addMarker(new MarkerOptions()
            .position(l)
            .icon(BitmapDescriptorFactory.fromResource(d))
            .anchor(0.3f, 1))
      }
    }

    /* Move map to bounds */
    mMap.setOnCameraChangeListener { p: CameraPosition ⇒
      flow {
        val mm = await(mMarkerManager)
        val rm = await(mRouteManager)
        switchToUiThread()
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(rm.getBounds, 30 dip))
        mMap.setOnCameraChangeListener { p: CameraPosition ⇒ mm.update() }
      }
    }
  }
}
