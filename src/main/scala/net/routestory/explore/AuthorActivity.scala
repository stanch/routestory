package net.routestory.explore

import net.routestory.parts.{ FragmentPaging, FragmentDataProvider, RouteStoryActivity }
import rx.Var
import scala.concurrent.Future
import android.os.Bundle
import scala.async.Async._
import android.widget.{ TextView, ImageView }
import android.view.ViewGroup.LayoutParams._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.model2.StoryPreview
import net.routestory.needs.NeedAuthor

class AuthorActivity extends RouteStoryActivity
  with HazStories
  with FragmentDataProvider[HazStories]
  with FragmentPaging {

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
      val author = await(NeedAuthor(id).go)
      name ~> text(author.name)
    }
    //if (stories.now.isCompleted) stories.update(fetchStories)
  }

  def fetchStories: Future[List[StoryPreview]] = async {
    ???
  }
}
