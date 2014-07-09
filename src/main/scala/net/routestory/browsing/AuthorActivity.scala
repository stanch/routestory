package net.routestory.browsing

import android.os.Bundle
import android.view.ViewGroup.LayoutParams._
import android.widget.{ ImageView, LinearLayout, TextView }
import macroid.FullDsl._
import macroid.IdGeneration
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.VerticalLinearLayout
import net.routestory.data.StoryPreview
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import rx.Var

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthorActivity extends RouteStoryActivity
  with HazStories
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
    setContentView(getUi(drawer(
      l[VerticalLinearLayout](
        w[ImageView] <~ lp[LinearLayout](100 dp, WRAP_CONTENT) <~ wire(picture) <~ hide,
        w[TextView] <~ lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT) <~ wire(name) <~ TextSize.medium,
        getTabs(
          "Stories" → f[StoriesListFragment].pass(
            "emptyText" → "No stories yet...",
            "errorText" → "Error occured :("
          ).factory
        )
      )
    )))
  }

  override def onStart() {
    super.onStart()
    async {
      val author = await(app.hybridApi.author(id).go)
      name <~ text(author.name)
    }
    //if (stories.now.isCompleted) stories.update(fetchStories)
  }

  def fetchStories: Future[List[StoryPreview]] = async {
    ???
  }
}
