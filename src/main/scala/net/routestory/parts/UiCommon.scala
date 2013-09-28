package net.routestory.parts

import org.scaloid.common._
import android.support.v4.app.Fragment
import net.routestory.{ MainActivity, StoryApplication }
import android.support.v4.app.FragmentActivity
import org.macroid._
import scala.concurrent.Promise
import android.view.{ MenuItem, View }
import android.view.animation.AlphaAnimation
import android.content.Intent
import scala.concurrent.ExecutionContext.Implicits.global

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

trait Animations extends Tweaks {
  val fadeIn = show +@ anim(new AlphaAnimation(0, 1), duration = 400)
  val fadeOut = anim(new AlphaAnimation(1, 0), duration = 400) @+ hide
}

trait StoryActivity extends FragmentActivity
  with FullDslActivity
  with FirstEveryStart
  with Animations {

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
  with FullDslFragment
  with Animations
  with FirstEveryStart {

  lazy val app = getActivity.getApplication.asInstanceOf[StoryApplication]

  override def onStart() {
    super.onStart()
    firstOrEvery()
  }
}