package net.routestory.explore

import scala.async.Async._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import android.os.Bundle
import android.view.ViewGroup.LayoutParams._
import android.widget.{ ImageView, TextView }

import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import rx.Var

import net.routestory.model.StoryPreview
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import net.routestory.util.FragmentDataProvider
import org.macroid.IdGeneration

class AuthorActivity extends RouteStoryActivity
  with HazStories
  with FragmentDataProvider[HazStories]
  with FragmentPaging
  with IdGeneration {

  lazy val stories: Var[Future[List[StoryPreview]]] = Var(fetchStories)
  def getFragmentData(tag: String): HazStories = this

  var picture = slot[ImageView]
  var name = slot[TextView]

  lazy val id = getIntent.getStringExtra("id")

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(drawer(
      l[VerticalLinearLayout](
        w[ImageView] ~> lp(100 dp, WRAP_CONTENT) ~> wire(picture) ~> hide,
        w[TextView] ~> lp(MATCH_PARENT, WRAP_CONTENT) ~> wire(name) ~> TextSize.medium,
        getTabs(
          "Stories" → f[StoriesListFragment].pass(
            "emptyText" → "No stories yet...",
            "errorText" → "Error occured :("
          ).factory
        )
      )
    ))
  }

  override def onStart() {
    super.onStart()
    async {
      val author = await(app.api.author(id).go)
      name ~> text(author.name)
    }
    //if (stories.now.isCompleted) stories.update(fetchStories)
  }

  def fetchStories: Future[List[StoryPreview]] = async {
    ???
  }
}
