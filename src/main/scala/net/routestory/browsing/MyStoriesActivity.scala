package net.routestory.browsing

import scala.async.Async._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import android.os.Bundle

import macroid.FullDsl._
import rx.Var

import net.routestory.R
import net.routestory.model.StoryPreview
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import net.routestory.util.FragmentDataProvider
import macroid.IdGeneration

class MyStoriesActivity extends RouteStoryActivity
  with HazStories
  with FragmentDataProvider[HazStories]
  with FragmentPaging
  with IdGeneration {

  lazy val stories: Var[Future[List[StoryPreview]]] = Var(fetchStories)
  def getFragmentData(tag: String): HazStories = this

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(getUi(drawer(getTabs(
      "Stories" → f[StoriesListFragment].pass(
        "emptyText" → getResources.getText(R.string.empty_mystories),
        "errorText" → "Error occured :("
      ).factory //,
    //"Map" → f[ResultMapFragment].factory
    ))))
  }

  override def onStart() {
    super.onStart()
    if (stories.now.isCompleted) stories.update(fetchStories)
  }

  def fetchStories: Future[List[StoryPreview]] = async {
    ???
  }
}