package net.routestory.parts

import org.scaloid.common._
import android.support.v4.app.{ ActionBarDrawerToggle, Fragment, FragmentActivity }
import net.routestory.{ R, AccountActivity, StoryApplication }
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
import android.os.Bundle

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
  var drawerToggle: ActionBarDrawerToggle = _

  override def onStart() {
    super.onStart()
    firstOrEvery()
  }

  override def onOptionsItemSelected(item: MenuItem) =
    Option(drawerToggle).map(_.onOptionsItemSelected(item)).filter(x ⇒ x).getOrElse(super.onOptionsItemSelected(item))

  override def onPostCreate(savedInstanceState: Bundle) {
    super.onPostCreate(savedInstanceState)
    Option(drawerToggle).map(_.syncState())
  }

  def drawer(view: View) = {
    def clicker[A <: Activity: ClassTag] = On.click {
      startActivityForResult(new Intent(this, implicitly[ClassTag[A]].runtimeClass), 0)
    }
    val layout = l[DrawerLayout](
      view ~> lp(MATCH_PARENT, MATCH_PARENT),
      l[VerticalLinearLayout](
        w[TextView] ~> text("Record") ~> clicker[RecordActivity],
        w[TextView] ~> text("My stories") ~> clicker[MyStoriesActivity],
        w[TextView] ~> text("Profile") ~> clicker[AccountActivity]
      ) ~> lp(240 dip, MATCH_PARENT, Gravity.START)
    )
    drawerToggle = new ActionBarDrawerToggle(
      this, layout, R.drawable.ic_navigation_drawer,
      R.string.app_name, R.string.app_name
    )
    layout.setDrawerListener(drawerToggle); layout
  }
}

trait StoryFragment extends Fragment with FullDslFragment with FirstEveryStart {
  lazy val app = getActivity.getApplication.asInstanceOf[StoryApplication]

  override def onStart() {
    super.onStart()
    firstOrEvery()
  }
}