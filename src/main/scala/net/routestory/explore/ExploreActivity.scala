package net.routestory.explore

import org.scaloid.common._
import android.os.Bundle
import android.view._
import android.widget._
import scala.concurrent._
import ExecutionContext.Implicits.global
import net.routestory.parts.{ WidgetFragment, GotoDialogFragments, StoryActivity }
import android.support.v4.app.Fragment
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import ViewGroup.LayoutParams._
import scala.async.Async.{ async, await }

class ExploreActivity extends StoryActivity {
  var progress: ProgressBar = _
  var retry: Button = _

  def latest = findFrag[Fragment with WidgetFragment](Tag.latest)
  def tags = findFrag[Fragment with WidgetFragment](Tag.tags)
  def search = findFrag[Fragment with WidgetFragment](Tag.search)

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val view = l[FrameLayout](
      l[ScrollView](
        l[VerticalLinearLayout](
          f[LatestFragment](Id.latest, Tag.latest, "number" → 4) ~> hide,
          f[TagsFragment](Id.tags, Tag.tags) ~> hide,
          f[SearchFragment](Id.search, Tag.search) ~> hide
        ) ~> p8dding
      ),
      l[FrameLayout](
        w[ProgressBar](null, android.R.attr.progressBarStyleLarge) ~>
          id(Id.progress) ~>
          lp(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER) ~>
          wire(progress),
        w[Button] ~>
          id(Id.retry) ~>
          lp(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER) ~>
          hide ~> text("Retry") ~> wire(retry) ⇝
          On.click {
            retry ~> hide
            progress ~> hide
            onFirstStart()
          }
      )
    )
    setContentView(view)
    bar.setDisplayHomeAsUpEnabled(true)
  }

  override def onFirstStart() {
    if (!GotoDialogFragments.ensureNetwork(this)) return

    async {
      await(latest.loaded.future zip tags.loaded.future zip search.loaded.future)
      await(fadeOut(findView(Id.progress)))
      await(fadeIn(findView(Id.latest)))
      await(fadeIn(findView(Id.tags)))
      await(fadeIn(findView(Id.search)))
    } onFailureUi {
      case t ⇒
        t.printStackTrace()
        progress ~> hide
        retry ~> show
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
}
