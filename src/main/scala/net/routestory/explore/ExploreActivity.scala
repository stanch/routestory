package net.routestory.explore

import org.scaloid.common._
import org.ektorp.ViewQuery
import net.routestory.parts.Implicits._
import android.os.Bundle
import android.location.LocationListener
import android.location.Location
import net.routestory.R
import android.content.Intent
import android.view._
import android.app.SearchManager
import android.widget._
import net.routestory.model._
import android.location.LocationManager
import scala.util.Random
import scala.concurrent._
import ExecutionContext.Implicits.global
import android.view.inputmethod.EditorInfo
import android.view.animation.AlphaAnimation
import java.util.Locale
import net.routestory.MainActivity
import net.routestory.parts.GotoDialogFragments
import net.routestory.parts.StoryActivity
import akka.dataflow._
import scala.collection.JavaConversions._
import android.widget.FrameLayout.LayoutParams
import net.routestory.parts.Animation._

class ExploreActivity extends StoryActivity {
    var flashed = false
    var mProgress: ProgressBar = null
    var mRetry: TextView = null

    def getBbox(loc: Location) = {
        // see [http://janmatuschek.de/LatitudeLongitudeBoundingCoordinates]
        val conv = Math.PI / 180
        val lat = loc.getLatitude * conv
        val lng = loc.getLongitude * conv
        val r = 0.5 // 500 meters
        val latr = r / 6371.0 // Earth radius
        val lngr = Math.asin(Math.sin(r) / Math.cos(lat)) // TODO: fix poles and 180 meridian
        "%f,%f,%f,%f".formatLocal(Locale.US, (lat - latr) / conv, (lng - lngr) / conv, (lat + latr) / conv, (lng + lngr) / conv)
    }

    override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        val view = new FrameLayout(ctx) {
            this += new FrameLayout(ctx) {
                this += getLayoutInflater.inflate(R.layout.activity_explore, this, false)
            }
            this += new FrameLayout(ctx) {
                mProgress = new ProgressBar(ctx, null, android.R.attr.progressBarStyleLarge) {
                    setLayoutParams(new LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                }
                this += mProgress
                mRetry = new Button(ctx) {
                    setVisibility(View.GONE)
                    setText("Retry") // TODO: strings.xml
                    setLayoutParams(new LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                    setOnClickListener { v: View ⇒
                        mRetry.setVisibility(View.GONE)
                        mProgress.setVisibility(View.VISIBLE)
                        start()
                    }
                }
                this += mRetry
            }
        }
        setContentView(view)
        bar.setDisplayHomeAsUpEnabled(true)
    }

    def getLocation: Future[Option[Location]] = {
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
        locationPromise.future
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
                val (stories, _, _) = await(app.getQueryResults[StoryResult](remote = true, query, None))
                val authors = await(app.getObjects[Author](stories.filter(_.authorId != null).map(_.authorId)))
                stories.filter(_.authorId != null).foreach(s ⇒ s.author = authors(s.authorId))
                switchToUiThread()
                val latestStories = find[LinearLayout](R.id.latestStories)
                latestStories.removeAllViews()
                stories foreach { s ⇒
                    latestStories.addView(ResultRow.getView(null, display.getWidth, s, ExploreActivity.this))
                }
            }

            // Stories nearby
            val nearby = flow {
                val location = await(getLocation)
                val result = location match {
                    case Some(loc) ⇒
                        val bbox = getBbox(loc)
                        //val query = new ViewQuery().designDocId("_design/Story").viewName("geoQuery").queryParam("bbox", bbox).limit(3)
                        //val stories = app.getQueryResults[StoryResult](remote = true, query).apply()
                        val stories = List[StoryResult]()
                        if (stories.isEmpty) {
                            None
                        } else {
                            val story = stories(Random.nextInt(stories.size))
                            story.author = await(app.getObject[Author](story.authorId))
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
                        showMap.setOnClickListener { v: View ⇒
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
                val tags = await(app.getPlainQueryResults(remote = true, query))
                switchToUiThread()
                val tagArray = Random.shuffle(tags.getRows.toList).take(10).map(_.getKey)
                ResultRow.fillTags(find[LinearLayout](R.id.storyPopularTagRows), display.getWidth - 20, tagArray.toArray, ExploreActivity.this)
            }

            // Search field
            find[TextView](R.id.searchField).setOnEditorActionListener { (v: TextView, actionId: Int, event: KeyEvent) ⇒
                if (Set(EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_DONE).contains(actionId) ||
                    event.getAction == KeyEvent.ACTION_DOWN && event.getKeyCode == KeyEvent.KEYCODE_ENTER) {
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
            await(latest zip nearby zip tags)

            if (!flashed) {
                flashed = true
                def fadeIn(view: View) = new AlphaAnimation(0, 1).duration(400).runOn(view)
                def fadeOut(view: View) = new AlphaAnimation(1, 0).duration(400).runOn(view, hideOnFinish = true)
                flow {
                    await(fadeOut(mProgress))
                    List(
                        R.id.latestStoriesSection, R.id.nearbyStoriesSection,
                        R.id.tagsSection, R.id.searchSection).cps[Future[Any]] foreach { id ⇒
                            await(fadeIn(find[LinearLayout](id)))
                        }
                }
            }
        } onFailureUi {
            case t ⇒
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
