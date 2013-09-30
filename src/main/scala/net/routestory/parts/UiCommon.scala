package net.routestory.parts

import org.scaloid.common._
import android.support.v4.app.Fragment
import net.routestory.{ AccountActivity, MainActivity, StoryApplication }
import android.support.v4.app.FragmentActivity
import org.macroid._
import android.view.{ Gravity, View, MenuItem }
import android.content.Intent
import android.support.v4.widget.DrawerLayout
import android.widget.{ TextView, ListView }
import android.view.ViewGroup.LayoutParams._
import android.app.Activity
import scala.reflect.ClassTag
import org.macroid.util.Thunk
import org.macroid.contrib.Layouts.VerticalLinearLayout
import net.routestory.recording.RecordActivity
import net.routestory.explore.MyStoriesActivity

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

trait StoryActivity extends FragmentActivity with FullDslActivity with FirstEveryStart {
  lazy val app = getApplication.asInstanceOf[StoryApplication]
  lazy val bar = getActionBar

  override def onStart() {
    super.onStart()
    firstOrEvery()
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case android.R.id.home ⇒
      val intent = SIntent[MainActivity]
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      startActivity(intent)
      true
    case _ ⇒ super.onOptionsItemSelected(item)
  }

  def drawer(view: View) = {
    def clicker[A <: Activity: ClassTag] = On.click {
      startActivityForResult(new Intent(this, implicitly[ClassTag[A]].runtimeClass), 0)
    }
    l[DrawerLayout](
      view ~> lp(MATCH_PARENT, MATCH_PARENT),
      l[VerticalLinearLayout](
        w[TextView] ~> text("Record") ~> clicker[RecordActivity],
        w[TextView] ~> text("My stories") ~> clicker[MyStoriesActivity],
        w[TextView] ~> text("Profile") ~> clicker[AccountActivity]
      ) ~> lp(240 dip, MATCH_PARENT, Gravity.START)
    )
  }
}

trait StoryFragment extends Fragment with FullDslFragment with FirstEveryStart {
  lazy val app = getActivity.getApplication.asInstanceOf[StoryApplication]

  override def onStart() {
    super.onStart()
    firstOrEvery()
  }
}