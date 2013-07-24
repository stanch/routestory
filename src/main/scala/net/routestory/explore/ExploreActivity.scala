package net.routestory.explore

import org.scaloid.common._
import android.os.Bundle
import android.view._
import android.widget._
import scala.concurrent._
import ExecutionContext.Implicits.global
import net.routestory.parts.{ WidgetFragment, GotoDialogFragments, StoryActivity }
import akka.dataflow._
import android.widget.FrameLayout.LayoutParams
import ViewGroup.LayoutParams._
import android.app.Fragment
import org.macroid.LayoutDsl
import org.macroid.Transforms._

class ExploreActivity extends StoryActivity with LayoutDsl {
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
          f[LatestFragment](Id.latest, Tag.latest, Map("number" → 4)) ~> hide,
          f[TagsFragment](Id.tags, Tag.tags, Map()) ~> hide,
          f[SearchFragment](Id.search, Tag.search, Map()) ~> hide
        ) ~> { x ⇒ x.setPaddingRelative(8 dip, 8 dip, 8 dip, 8 dip) }
      ),
      l[FrameLayout](
        w[ProgressBar](null, android.R.attr.progressBarStyleLarge) ~>
          id(Id.progress) ~>
          center() ~>
          wire(progress),
        w[Button] ~> id(Id.retry) ~> center() ~> hide ~> text("Retry") ~> wire(retry) ~> { x ⇒
          x.setOnClickListener { v: View ⇒
            x ~> hide
            progress ~> hide
            onFirstStart()
          }
        }
      )
    )
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
