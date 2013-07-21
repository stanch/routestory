package net.routestory.parts

import org.scaloid.common._
import android.app.Fragment
import net.routestory.StoryApplication
import android.app.Activity
import org.macroid._

trait FirstEveryStart {
  var everStarted = false
  def onFirstStart() {}
  def onEveryStart() {}
  def firstOrEvery() {
    if (!everStarted) {
      onFirstStart()
      everStarted = true
    }
    onEveryStart()
  }
}

trait StoryActivity extends Activity
  with Concurrency
  with ActivityViewSearch
  with FirstEveryStart
  with Fragments
  with SActivity {
  lazy val app = getApplication.asInstanceOf[StoryApplication]
  lazy val bar = getActionBar

  override def onStart() {
    super.onStart()
    firstOrEvery()
  }
}

trait StoryFragment extends Fragment
  with Concurrency
  with FragmentViewSearch
  with Fragments
  with FirstEveryStart {
  lazy val app = getActivity.getApplication.asInstanceOf[StoryApplication]
  implicit lazy val ctx = getActivity

  override def onStart() {
    super.onStart()
    firstOrEvery()
  }
}