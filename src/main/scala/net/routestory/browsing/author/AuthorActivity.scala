package net.routestory.browsing.author

import android.os.Bundle
import android.view.ViewGroup.LayoutParams._
import android.widget.{ ImageView, LinearLayout, TextView }
import macroid.FullDsl._
import macroid.IdGeneration
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.contrib.{ LpTweaks, TextTweaks }
import net.routestory.browsing.stories.OnlineFragment
import net.routestory.data.StoryPreview
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import rx.Var

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthorActivity extends RouteStoryActivity
  with FragmentPaging
  with IdGeneration {

  lazy val stories: Var[Future[List[StoryPreview]]] = Var(fetchStories)

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
        w[TextView] <~ LpTweaks.matchWidth <~ wire(name) <~ TextTweaks.medium,
        getTabs(
          "Stories" → f[OnlineFragment].factory
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
