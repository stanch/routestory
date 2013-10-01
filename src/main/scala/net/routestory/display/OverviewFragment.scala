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
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scaloid.common._
import net.routestory.parts.StoryFragment
import net.routestory.parts.Implicits._
import scala.concurrent.Future
import android.widget.{ Button, FrameLayout }
import ViewGroup.LayoutParams._
import scala.async.Async.{ async, await }

class OverviewFragment extends StoryFragment {
  lazy val mStory = getActivity.asInstanceOf[HazStory].getStory
  lazy val display = getActivity.getWindowManager.getDefaultDisplay
  lazy val mMap = findFrag[SupportMapFragment](Tag.overviewMap).get.getMap
  lazy val mRouteManager = async {
    val story = await(mStory)
    val rm = new RouteManager(mMap, story)
    Ui(rm.init())
    rm
  }
  lazy val mMarkerManager = async {
    val story = await(mStory)
    new MarkerManager(mMap, List(display.getWidth(), display.getHeight()), story, getActivity)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[FrameLayout](
      l[FrameLayout](
        f[SupportMapFragment](Id.map, Tag.overviewMap)
      ),
      l[FrameLayout](
        w[Button] ~>
          text(R.string.hide_overlays) ~>
          lp(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL) ~>
          FuncOn.click { x: View ⇒
            mMarkerManager onSuccessUi {
              case mm ⇒
                mm.hide_overlays = !mm.hide_overlays
                x.asInstanceOf[Button] ~> text(if (mm.hide_overlays) R.string.show_overlays else R.string.hide_overlays)
                mm.update()
            }
          }
      )
    )
  }

  override def onFirstStart() {
    mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID)

    /* Initialize marker manager */
    async {
      val mm = await(mMarkerManager)
      val progress = await(Ui(new ProgressDialog(ctx) {
        setMessage("Loading data...")
        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        setMax(mm.loadingItems.length)
        show()
      }))
      mm.loadingItems.foreach(_.onSuccessUi {
        case _ ⇒
          progress.incrementProgressBy(1)
      })
      Future.sequence(mm.loadingItems).onSuccessUi {
        case _ ⇒
          progress.dismiss()
      }
      Ui {
        mm.update()
        mMap.setOnMarkerClickListener(mm.onMarkerClick _)
      }
    }

    /* Put start and end markers */
    async {
      val rm = await(mRouteManager)
      List(rm.getStart → R.drawable.flag_start, rm.getEnd → R.drawable.flag_end) map {
        case (l, d) ⇒
          Ui(mMap.addMarker(new MarkerOptions()
            .position(l)
            .icon(BitmapDescriptorFactory.fromResource(d))
            .anchor(0.3f, 1)))
      }
    }

    /* Move map to bounds */
    mMap.setOnCameraChangeListener { p: CameraPosition ⇒
      async {
        val mm = await(mMarkerManager)
        val rm = await(mRouteManager)
        Ui {
          mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(rm.getBounds, 30 dip))
          mMap.setOnCameraChangeListener { p: CameraPosition ⇒ mm.update() }
        }
      }
    }
  }
}
