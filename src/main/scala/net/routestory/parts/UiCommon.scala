package net.routestory.parts

import org.scaloid.common._
import android.support.v4.app.{ ActionBarDrawerToggle, Fragment, FragmentActivity }
import net.routestory.{ SettingsActivity, R, AccountActivity, RouteStoryApp }
import org.macroid._
import android.view.{ ViewGroup, Gravity, View, MenuItem }
import android.content.{ Context, Intent }
import android.support.v4.widget.DrawerLayout
import android.widget.{ ProgressBar, TextView, ListView }
import android.view.ViewGroup.LayoutParams._
import android.app.Activity
import scala.reflect.ClassTag
import org.macroid.util.Thunk
import org.macroid.contrib.Layouts.VerticalLinearLayout
import net.routestory.recording.RecordActivity
import net.routestory.explore.{ ExploreActivity, MyStoriesActivity }
import android.os.Bundle
import org.macroid.contrib.ExtraTweaks.{ TextSize, TextStyle }

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

trait RouteStoryActivity extends FragmentActivity with FullDslActivity with FirstEveryStart {
  lazy val app = getApplication.asInstanceOf[RouteStoryApp]
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

  def activityProgress(implicit ctx: Context) =
    w[ProgressBar](null, android.R.attr.progressBarStyleHorizontal) ~>
      lpOf[ViewGroup](MATCH_PARENT, WRAP_CONTENT) ~>
      (_.setBackgroundColor(0xff101010))

  def drawer(view: View) = {
    def clicker[A <: Activity: ClassTag] = On.click {
      startActivityForResult(new Intent(this, implicitly[ClassTag[A]].runtimeClass), 0)
    }
    // format: OFF
    val data = Seq(
      ("Explore", clicker[ExploreActivity]),
      ("Create a story", clicker[RecordActivity]),
      ("My stories", clicker[MyStoriesActivity]),
      ("Profile", clicker[AccountActivity]),
      ("Settings", clicker[SettingsActivity])
    )
    val adapter = AwesomeAdapter.simple(data)(
      w[TextView] ~> TextSize.medium ~> padding(all = 10 dip) ~> TextStyle.boldItalic,
      data ⇒ text(data._1) + data._2
    )
    // format: ON
    val layout = l[DrawerLayout](
      view ~> lp(MATCH_PARENT, MATCH_PARENT),
      w[ListView] ~>
        (_.setAdapter(adapter)) ~>
        lp(240 dip, MATCH_PARENT, Gravity.START) ~>
        (_.setBackgroundColor(0xf0141414))
    )
    drawerToggle = new ActionBarDrawerToggle(
      this, layout, R.drawable.ic_navigation_drawer,
      R.string.app_name, R.string.app_name
    )
    layout.setDrawerListener(drawerToggle); layout
  }
}

trait RouteStoryFragment extends Fragment with FullDslFragment with FirstEveryStart {
  lazy val app = getActivity.getApplication.asInstanceOf[RouteStoryApp]

  override def onStart() {
    super.onStart()
    firstOrEvery()
  }
}