package net.routestory.parts

import org.scaloid.common._
import android.app.Fragment
import net.routestory.StoryApplication
import android.app.Activity
import org.macroid._
import scala.concurrent.Promise
import android.view.View
import android.view.animation.AlphaAnimation

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

trait WidgetFragment {
  val loaded = Promise[Any]()
}

trait Animations {
  import Animation._

  def fadeIn(view: View) = new AlphaAnimation(0, 1).duration(400).runOn(view)
  def fadeOut(view: View) = new AlphaAnimation(1, 0).duration(400).runOn(view, hideOnFinish = true)
}

trait StoryActivity extends Activity
  with Concurrency
  with ActivityViewSearch
  with FirstEveryStart
  with Fragments
  with Layouts
  with Animations
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
  with Layouts
  with Animations
  with FirstEveryStart {
  lazy val app = getActivity.getApplication.asInstanceOf[StoryApplication]
  implicit def ctx = getActivity

  override def onStart() {
    super.onStart()
    firstOrEvery()
  }
}