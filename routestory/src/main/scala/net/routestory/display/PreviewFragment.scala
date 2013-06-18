package net.routestory.display;

import java.io.File
import net.routestory.R
import net.routestory.StoryApplication
import net.routestory.model.Story
import net.routestory.parts.BitmapUtils
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.os.Vibrator
import android.support.v4.app.FragmentTransaction
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.actionbarsherlock.app.SherlockFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.scaloid.common._
import net.routestory.parts.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions._
import android.app.ProgressDialog
import net.routestory.parts.StoryFragment

class PreviewFragment extends SherlockFragment with StoryFragment {
	lazy val mMap = findFrag[SupportMapFragment]("preview_map").getMap()
	lazy val mStory = getActivity.asInstanceOf[HazStory].getStory
	lazy val mRouteManager = mStory map { new RouteManager(mMap, _) } map { x => runOnUiThread{x.init()}; x }
	lazy val mHandler = new Handler()
	
	var mPlayButton: Button = null
	var mImageView: ImageView = null
	var mMediaPlayer: MediaPlayer = null
	var mPositionMarker: Marker = null

	override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
	    val view = new SFrameLayout {
	        this += new SFrameLayout {
	            this += new SFrameLayout().id(1)
	        }
	        this += new SFrameLayout {
	            mImageView = new ImageView(ctx)
	        }
	        this += new SFrameLayout {
	            mPlayButton = SButton(R.string.play).<<(WRAP_CONTENT, WRAP_CONTENT).marginBottom(20 dip).Gravity(Gravity.CENTER|Gravity.CENTER_HORIZONTAL).>>
	            mPlayButton.setOnClickListener { v: View =>
	                mStory zip mRouteManager onSuccessUI { case (x, y) => startPreview(x, y) }
		        }
	        }
	    }
	    
		if (findFrag("preview_map") == null) {
			val mapFragment = SupportMapFragment.newInstance()
			val fragmentTransaction = getChildFragmentManager().beginTransaction()
	        fragmentTransaction.add(1, mapFragment, "preview_map")
	        fragmentTransaction.commit()
		}
	    
		view
	}
	
	override def onFirstStart() {
	    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)
	    mRouteManager onSuccessUI { case rm =>
	        mPositionMarker = mMap.addMarker(new MarkerOptions()
	        	.position(rm.getStart())
	        	.icon(BitmapDescriptorFactory.fromResource(R.drawable.man))
	        )
	    } onFailureUI {
	        case t => t.printStackTrace()
	    }
        
        val display = getActivity().getWindowManager().getDefaultDisplay()        
		val maxSize = Math.min(display.getWidth(), display.getHeight())/4
		
        mStory onSuccessUI { case story => 
            val progress = new ProgressDialog(ctx)
            progress.setMessage("Loading data...")
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            progress.setMax( story.photos.length + 1 )
            progress.show()
            
            val photos = story.photos map { i =>
	            i.get(maxSize) onSuccessUI { case bitmap if bitmap != null =>
					mMap.addMarker(new MarkerOptions()
				    	.position(story.getLocation(i.timestamp))
				    	.icon(BitmapDescriptorFactory.fromBitmap(BitmapUtils.createScaledTransparentBitmap(
			    			bitmap, Math.min(Math.max(bitmap.getWidth(), bitmap.getHeight()), maxSize),
	    					0.8, false
						)))
				    )
	            } onSuccessUI { case _ =>
	                progress.incrementProgressBy(1)
	            }
            }
            
            val audio = story.audioPreview.get() onSuccessUI { case _ =>
            	progress.incrementProgressBy(1)
            }
            
            Future.sequence(audio +: photos) onSuccessUI { case _ =>
                progress.dismiss()
            }
            
        }
	}

    override def onEveryStart() {
        mRouteManager onSuccessUI { case rm =>
	        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder()
	    		.target(rm.getStart()).tilt(90).zoom(19)
	    		.bearing(rm.getStartBearing()).build()
			));
        }
    }
    
    override def onStop() {
    	super.onStop();
    	mHandler.removeCallbacksAndMessages(null);
    }
    
    def startPreview(story: Story, rm: RouteManager) {
    	mPlayButton.setVisibility(View.INVISIBLE);
	    val start = SystemClock.uptimeMillis();
    	val ratio = story.duration.toDouble / StoryApplication.storyPreviewDuration / 1000;
    	val lastLocation = story.locations.last.timestamp / ratio;
    	
    	story.notes foreach { note =>
    		mHandler.postAtTime({
    			toast(note.text)
    		}, start + (note.timestamp/ratio).toInt);
    	}
    	
    	story.heartbeat foreach { beat =>
    		mHandler.postAtTime({
    			vibrator.vibrate(beat.getVibrationPattern(4), -1);
    		}, start + (beat.timestamp/ratio).toInt);
    	}
    	
    	def move() {
			val elapsed = SystemClock.uptimeMillis() - start;
			val now = story.getLocation(elapsed*ratio);
			
			val bearing = (List(100, 300, 500) map { t =>
			    rm.getBearing(now, story.getLocation((elapsed+t)*ratio))
			} zip List(0.3f, 0.4f, 0.3f) map { case (b, w) =>
			    b * w
			}).sum
			
			mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(mMap.getCameraPosition()).target(now).bearing(bearing).build()));
			if (elapsed < lastLocation) {
				mHandler.postDelayed(move, 300);
			}
    	}
    	mHandler.postDelayed(move, 300);
    	
    	def walk() {
			val elapsed = SystemClock.uptimeMillis() - start;
			val now = story.getLocation(elapsed*ratio);
			mPositionMarker.remove();
			mPositionMarker = mMap.addMarker(new MarkerOptions()
            	.position(now)
            	.icon(BitmapDescriptorFactory.fromResource(R.drawable.man))
            );
			if (elapsed < lastLocation) {
				mHandler.postDelayed(walk, 100);
			}
    	}
    	mHandler.postDelayed(walk, 100);
    	
    	mHandler.postDelayed({
			rewind();
    	}, (StoryApplication.storyPreviewDuration+3)*1000);
    	
    	playAudio(story);
    }
    
    def rewind() {
    	mPlayButton.setVisibility(View.VISIBLE);
    	mRouteManager onSuccessUI { case rm =>
			mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder()
	    		.target(rm.getStart()).tilt(90).zoom(19)
	    		.bearing(rm.getStartBearing()).build()
			));
			mPositionMarker.remove();
			mPositionMarker = mMap.addMarker(new MarkerOptions()
		    	.position(rm.getStart())
		    	.icon(BitmapDescriptorFactory.fromResource(R.drawable.man))
		    );
    	}
    }
    
    def playAudio(story: Story) {
    	mMediaPlayer = new MediaPlayer();
    	if (story.audioPreview == null) return;
    	story.audioPreview.get() onSuccessUI { case file =>
    	    if (file == null) return;
    	    try {
				mMediaPlayer.setDataSource(file.getAbsolutePath());
				mMediaPlayer.prepare();
	            mMediaPlayer.start();
			} catch {
				case e: Throwable => e.printStackTrace();
			}
    	}
    }
}
