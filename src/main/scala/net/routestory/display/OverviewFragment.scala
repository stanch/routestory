package net.routestory.display

import net.routestory.R
import net.routestory.model.Story
import android.app.ProgressDialog
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.FragmentTransaction
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.Button
import android.widget.FrameLayout
import com.actionbarsherlock.app.SherlockFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import net.routestory.parts.StoryFragment
import net.routestory.parts.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import org.scaloid.common._
import org.scaloid.common.SButton
import android.graphics.Point
import net.routestory.parts.StoryFragment

class OverviewFragment extends SherlockFragment with StoryFragment {
    lazy val display = getActivity.getWindowManager.getDefaultDisplay
    lazy val mMap = findFrag[SupportMapFragment]("overview_map").getMap()
	lazy val mStory = getActivity.asInstanceOf[HazStory].getStory
	lazy val mRouteManager = mStory map { new RouteManager(mMap, _) } map { x ⇒ runOnUiThread{x.init()}; x }
    lazy val mMarkerManager = mStory map { new MarkerManager(mMap, List(display.getWidth(), display.getHeight()), _) }
	
	override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
	    val view = new SFrameLayout {
	        this += new SFrameLayout {
	            this += new SFrameLayout().id(1)
	        }
	        this += new SFrameLayout {
	            val toggleOverlays = SButton(R.string.hide_overlays).<<(WRAP_CONTENT, WRAP_CONTENT).marginBottom(20 dip).Gravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL).>>
	            toggleOverlays.setOnClickListener { v: View =>
					mMarkerManager onSuccessUI { case mm =>
					    mm.hide_overlays = !mm.hide_overlays
					    toggleOverlays.setText(if (mm.hide_overlays) R.string.show_overlays else R.string.hide_overlays)
					    mm.update()
					}
		        }
	        }
	    }
	    
		if (findFrag("overview_map") == null) {
			val mapFragment = SupportMapFragment.newInstance()
			val fragmentTransaction = getChildFragmentManager().beginTransaction()
	        fragmentTransaction.add(1, mapFragment, "overview_map")
	        fragmentTransaction.commit()
		}

		view
	}

    override def onFirstStart() {
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID)
        
        /* Initialize marker manager */
        //val progress = spinnerDialog("", "Putting everying on the map...")
        mMarkerManager map { case mm =>
            while (!mm.isReady) {}
            mm
        } onSuccessUI { case mm =>
            mm.update();
    		mMap.setOnMarkerClickListener(mm.onMarkerClick _);
    		//progress.dismiss();
        }
        
        /* Put start and end markers */
        mRouteManager onSuccessUI { case rm =>
	        List(rm.getStart(), rm.getEnd()) zip
	        List(R.drawable.flag_start, R.drawable.flag_end) map { case (l, d) =>
	        	mMap.addMarker(new MarkerOptions()
					.position(l)
			    	.icon(BitmapDescriptorFactory.fromResource(d))
			    	.anchor(0.3f, 1)
				);
	        }
        }
        
        /* Move map to bounds */
        mMap.setOnCameraChangeListener { p: CameraPosition =>
            mMarkerManager zip mRouteManager onSuccessUI { case (mm, rm) =>
			    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(rm.getBounds(), 30 dip))
			    mMap.setOnCameraChangeListener { p: CameraPosition =>
			    	mm.update();
			    }
            }
        }
    }
}
