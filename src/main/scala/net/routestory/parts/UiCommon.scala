package net.routestory.parts

import org.scaloid.common._
import android.app.Fragment
import net.routestory.{ MainActivity, StoryApplication }
import android.app.Activity
import org.macroid._
import scala.concurrent.Promise
import android.view.{ MenuItem, View }
import android.view.animation.AlphaAnimation
import android.content.Intent

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

trait FragmentDataProvider[A] {
  def getFragmentData(tag: String): A
}

trait FragmentData[A] { self: Fragment ⇒
  def getFragmentData = getActivity.asInstanceOf[FragmentDataProvider[A]].getFragmentData(getTag)
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

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case android.R.id.home ⇒ {
        val intent = SIntent[MainActivity]
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        true
      }
      case _ ⇒ super.onOptionsItemSelected(item)
    }
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