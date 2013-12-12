package net.routestory.explore

import net.routestory.parts.{ FragmentPaging, FragmentDataProvider, RouteStoryActivity }
import rx.Var
import scala.concurrent.Future
import android.os.Bundle
import org.macroid.util.Text
import net.routestory.R
import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.model.StoryPreview

class MyStoriesActivity extends RouteStoryActivity
  with HazStories
  with FragmentDataProvider[HazStories]
  with FragmentPaging {

  lazy val stories: Var[Future[List[StoryPreview]]] = Var(fetchStories)
  def getFragmentData(tag: String): HazStories = this

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(drawer(getTabs(
      "Stories" → f[StoriesListFragment].pass(
        "emptyText" → Text(R.string.empty_mystories),
        "errorText" → "Error occured :("
      ).factory //,
    //"Map" → f[ResultMapFragment].factory
    )))
  }

  override def onStart() {
    super.onStart()
    if (stories.now.isCompleted) stories.update(fetchStories)
  }

  def fetchStories: Future[List[StoryPreview]] = async {
    ???
  }
}