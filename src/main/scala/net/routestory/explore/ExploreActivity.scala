package net.routestory.explore

import org.scaloid.common._
import android.os.Bundle
import android.content.Intent
import android.view._
import android.widget._
import scala.concurrent._
import ExecutionContext.Implicits.global
import net.routestory.MainActivity
import net.routestory.parts.GotoDialogFragments
import net.routestory.parts.StoryActivity
import akka.dataflow._
import android.widget.FrameLayout.LayoutParams
import ViewGroup.LayoutParams._

class ExploreActivity extends StoryActivity {
  lazy val progress = findView[ProgressBar](Id.progress)
  lazy val retry = findView[Button](Id.retry)

  lazy val latest = new LatestFragment(4)
  lazy val tags = new TagsFragment
  lazy val search = new SearchFragment

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val view = new FrameLayout(ctx) {
      this += new ScrollView(ctx) {
        this += new VerticalLinearLayout(ctx) {
          setPaddingRelative(8 dip, 8 dip, 8 dip, 8 dip)
          this += fragment(latest, Id.latest, Tag.latest, hide = true)
          this += fragment(tags, Id.tags, Tag.tags, hide = true)
          this += fragment(search, Id.search, Tag.search, hide = true)
        }
      }
      this += new FrameLayout(ctx) {
        this += new ProgressBar(ctx, null, android.R.attr.progressBarStyleLarge) {
          setId(Id.progress)
          setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        }
        this += new Button(ctx) { self ⇒
          setId(Id.retry)
          setVisibility(View.GONE)
          setText("Retry") // TODO: strings.xml
          setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
          setOnClickListener { v: View ⇒
            self.setVisibility(View.GONE)
            findView[ProgressBar](Id.progress).setVisibility(View.VISIBLE)
            onFirstStart()
          }
        }
      }
    }
    setContentView(view)
    bar.setDisplayHomeAsUpEnabled(true)
  }

  override def onFirstStart() {
    if (!GotoDialogFragments.ensureNetwork(this)) return

    flow {
      await(latest.loaded.future zip tags.loaded.future zip search.loaded.future)
      await(fadeOut(findView(Id.progress)))
      await(fadeIn(findView(Id.latest)))
      await(fadeIn(findView(Id.tags)))
      await(fadeIn(findView(Id.search)))
    } onFailureUi {
      case t ⇒
        t.printStackTrace()
        progress.setVisibility(View.GONE)
        retry.setVisibility(View.VISIBLE)
    }

    // Stories nearby
    //      val nearby = flow {
    //        val location = await(getLocation)
    //        val result = location match {
    //          case Some(loc) ⇒
    //            val bbox = getBbox(loc)
    //            //val query = new ViewQuery().designDocId("_design/Story").viewName("geoQuery").queryParam("bbox", bbox).limit(3)
    //            //val stories = app.getQueryResults[StoryResult](remote = true, query).apply()
    //            val stories = List[StoryResult]()
    //            if (stories.isEmpty) {
    //              None
    //            } else {
    //              val story = stories(Random.nextInt(stories.size))
    //              story.author = await(app.getObject[Author](story.authorId))
    //              Some((bbox, story))
    //            }
    //          case None ⇒ None
    //        }
    //        switchToUiThread()
    //        result match {
    //          case Some((bbox, story)) ⇒ {
    //            // something found
    //            find[TextView](R.id.nothingNearby).setVisibility(View.GONE)
    //            val nearbyStories = find[LinearLayout](R.id.nearbyStories)
    //            nearbyStories.removeAllViews()
    //            val showMap = find[TextView](R.id.showNearbyMap)
    //            showMap.setVisibility(View.VISIBLE)
    //            showMap.setOnClickListener { v: View ⇒
    //              val intent = SIntent[SearchResultsActivity]
    //              intent.putExtra("showmap", true)
    //              intent.putExtra("bbox", bbox)
    //              startActivityForResult(intent, 0)
    //            }
    //            nearbyStories.addView(ResultRow.getView(null, display.getWidth, story, ExploreActivity.this))
    //          }
    //          case None ⇒ {
    //            find[LinearLayout](R.id.nearbyStories).removeAllViews()
    //            find[TextView](R.id.showNearbyMap).setVisibility(View.GONE)
    //            find[TextView](R.id.nothingNearby).setVisibility(View.VISIBLE)
    //          }
    //        }
    //      }
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
