package net.routestory.explore

import org.scaloid.common._
import org.ektorp.ViewQuery
import net.routestory.parts.Implicits._
import android.os.Bundle
import android.location.LocationListener
import com.actionbarsherlock.app.SherlockFragmentActivity
import android.location.Location
import com.actionbarsherlock.view.MenuItem
import net.routestory.R
import android.content.Intent
import android.view.View
import android.app.SearchManager
import android.widget.LinearLayout
import android.widget.TextView
import net.routestory.model._
import android.location.LocationManager
import scala.util.Random
import scala.concurrent._
import ExecutionContext.Implicits.global
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.animation.Animation
import android.view.animation.AlphaAnimation
import android.view.animation.Animation.AnimationListener
import android.view.Gravity
import android.widget.ProgressBar
import java.util.Locale
import net.routestory.MainActivity
import net.routestory.parts.GotoDialogFragments
import net.routestory.parts.StoryActivity
import akka.dataflow._
import scala.collection.JavaConversions._

class ExploreActivity extends SherlockFragmentActivity with StoryActivity {
    var flashed = false
    var mProgress: ProgressBar = null
    var mRetry: TextView = null
    
	def getBbox(loc: Location) = {
		// see [http://janmatuschek.de/LatitudeLongitudeBoundingCoordinates]
		val conv = Math.PI/180
		val lat = loc.getLatitude*conv
		val lng = loc.getLongitude*conv
		val r = 0.5 // 500 meters
		val latr = r / 6371.0 // Earth radius
		val lngr = Math.asin(Math.sin(r)/Math.cos(lat)) // TODO: fix poles and 180 meridian
		"%f,%f,%f,%f".formatLocal(Locale.US, (lat-latr)/conv, (lng-lngr)/conv, (lat+latr)/conv, (lng+lngr)/conv)
	}
	
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		val view = new SFrameLayout {
		    this += new SFrameLayout {
		        this += getLayoutInflater().inflate(R.layout.activity_explore, this, false)
		    }
		    this += new SFrameLayout {
		        mProgress = new ProgressBar(ctx, null, android.R.attr.progressBarStyleLarge)
		        mProgress.Gravity(Gravity.CENTER)
		        this += mProgress
		        mRetry = SButton("Retry").<<(WRAP_CONTENT, WRAP_CONTENT).>>
		        mRetry.Gravity(Gravity.CENTER)
		        mRetry.setVisibility(View.GONE)
		        mRetry onClick {
		            mRetry.setVisibility(View.GONE)
		            mProgress.setVisibility(View.VISIBLE)
		            start()
		        }
		    }
		}
		setContentView(view)
	    getSupportActionBar.setDisplayHomeAsUpEnabled(true)
	}
	
	def getLocation: Promise[Option[Location]] = {
	    val locationPromise = Promise[Option[Location]]()
	    val locationListener = new LocationListener() {
	        def onLocationChanged(location: Location) {
                locationPromise.trySuccess(Some(location))
				locationManager.removeUpdates(this)
		    }
		    override def onStatusChanged(provider: String, status: Int, extras: Bundle) {}
		    override def onProviderEnabled(provider: String) {}
		    override def onProviderDisabled(provider: String) {}
	    }
	    runOnUiThread {
	    	locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, locationListener)
	    	locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, locationListener)
	    }
	    future {
	        Thread.sleep(3000)
	        locationPromise.trySuccess(None)
	    }
    	locationPromise
	}
	
	abstract class RichAnimation(view: View) {
	    def getAnim: Animation
	    
	    def onFinish(f: => Any): RichAnimation = {
	        getAnim.setAnimationListener(new AnimationListener() {
	            override def onAnimationStart(a: Animation) {}
	            override def onAnimationRepeat(a: Animation) {}
	            override def onAnimationEnd(a: Animation) = f
	        })
	        this
	    }
	    
	    def run() {
		    view.setVisibility(View.VISIBLE)
		    view.startAnimation(getAnim)
		}
	}
	
	class FadeIn(view: View) extends RichAnimation(view) {
	    val fade = new AlphaAnimation(0, 1)
	    fade.setDuration(400)
	    override def getAnim = fade
	}
	
	class FadeOut(view: View) extends RichAnimation(view) {
	    val fade = new AlphaAnimation(1, 0)
	    fade.setDuration(400)
	    override def getAnim = fade
	}
	
	override def onStart() {
		super.onStart()
		start()
	}
	
	def start() {
		if (!GotoDialogFragments.ensureNetwork(this)) return
		
		// get screen size
		val display = getWindowManager.getDefaultDisplay

        flow {
            // Latest stuff
            val latest = flow {
                val query = new ViewQuery().designDocId("_design/Story").viewName("byTimestamp").descending(true).limit(3)
                val stories = app.getQueryResults[StoryResult](remote = true, query).apply()
                val authors = app.getObjects[Author](stories.filter(_.authorId!=null).map(_.authorId)).apply()
                stories.filter(_.authorId!=null).foreach(s ⇒ s.author = authors(s.authorId))
                switchToUiThread()
                val latestStories = find[LinearLayout](R.id.latestStories)
                latestStories.removeAllViews()
                stories foreach { s ⇒
                    latestStories.addView(ResultRow.getView(null, display.getWidth, s, ExploreActivity.this))
                }
            }

            // Stories nearby
            val nearby = flow {
                val location = getLocation.future.apply()
                // Option.flatMap does not seem to work with @cps
                val result = location match {
                    case Some(loc) ⇒
                        val bbox = getBbox(loc)
                        val query = new ViewQuery().designDocId("_design/Story").viewName("geoQuery").queryParam("bbox", bbox).limit(3)
                        val stories = app.getQueryResults[StoryResult](remote = true, query).apply()
                        if (stories.isEmpty) {
                            None
                        } else {
                            val story = stories(Random.nextInt(stories.size))
                            story.author = app.getObject[Author](story.authorId).apply()
                            Some((bbox, story))
                        }
                    case None ⇒ None
                }
                switchToUiThread()
                result match {
                    case Some((bbox, story)) ⇒ {
                        // something found
                        find[TextView](R.id.nothingNearby).setVisibility(View.GONE)
                        val nearbyStories = find[LinearLayout](R.id.nearbyStories)
                        nearbyStories.removeAllViews()
                        val showMap = find[TextView](R.id.showNearbyMap)
                        showMap.setVisibility(View.VISIBLE)
                        showMap.setOnClickListener { v: View =>
                            val intent = SIntent[SearchResultsActivity]
                            intent.putExtra("showmap", true)
                            intent.putExtra("bbox", bbox)
                            startActivityForResult(intent, 0)
                        }
                        nearbyStories.addView(ResultRow.getView(null, display.getWidth, story, ExploreActivity.this))
                    }
                    case None ⇒ {
                        find[LinearLayout](R.id.nearbyStories).removeAllViews()
                        find[TextView](R.id.showNearbyMap).setVisibility(View.GONE)
                        find[TextView](R.id.nothingNearby).setVisibility(View.VISIBLE)
                    }
                }
            }

            // Popular tags
            val tags = flow {
                val query = new ViewQuery().designDocId("_design/Story").viewName("tags").group(true)
                val tags = app.getPlainQueryResults(remote = true, query).apply()
                switchToUiThread()
                val tagArray = Random.shuffle(tags.getRows.toList) take(10) map (_.getKey)
                ResultRow.fillTags(find[LinearLayout](R.id.storyPopularTagRows), display.getWidth-20, tagArray.toArray, ExploreActivity.this)
            }

            // Search field
            find[TextView](R.id.searchField).setOnEditorActionListener { (v: TextView, actionId: Int, event: KeyEvent) =>
                if (
                    List(EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_DONE).contains(actionId) ||
                    event.getAction == KeyEvent.ACTION_DOWN && event.getKeyCode == KeyEvent.KEYCODE_ENTER
                ) {
                    val intent = SIntent[SearchResultsActivity]
                    intent.setAction(Intent.ACTION_SEARCH)
                    intent.putExtra(SearchManager.QUERY, find[TextView](R.id.searchField).getText.toString)
                    startActivityForResult(intent, 0)
                    true
                } else {
                    false
                }
            }

            // wait till they finish
            latest(); nearby(); tags()

            if (!flashed) {
                switchToUiThread()
                new FadeOut(mProgress).onFinish{ mProgress.setVisibility(View.GONE) }.run()
                val head::tail = List(R.id.latestStoriesSection, R.id.nearbyStoriesSection, R.id.tagsSection, R.id.searchSection) map { id =>
                    find[LinearLayout](id)
                }
                val first = new FadeIn(head)
                tail.foldLeft(first) { (fade, view) =>
                    val next = new FadeIn(view)
                    fade.onFinish(next.run)
                    next
                }
                first.run()
                flashed = true
            }
		} onFailureUI { case t =>
		    t.printStackTrace()
		    mProgress.setVisibility(View.GONE)
		    mRetry.setVisibility(View.VISIBLE)
		}
	}
	
	override def onOptionsItemSelected(item: MenuItem) = {
	    item.getItemId match {
	        case android.R.id.home ⇒
	            val intent = SIntent[MainActivity]
	            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
	            startActivity(intent)
	            true
	        case _ ⇒ false
	    }
	}
}
