package net.routestory.ui

import scala.reflect.ClassTag

import android.app.{ Activity, SearchManager }
import android.content.{ Context, Intent }
import android.graphics.Point
import android.os.Bundle
import android.support.v4.app.{ ActionBarDrawerToggle, Fragment, FragmentActivity }
import android.support.v4.widget.DrawerLayout
import android.view._
import android.view.ViewGroup.LayoutParams._
import android.widget.{ ListView, ProgressBar, SearchView }

import org.macroid.FullDsl._
import org.macroid.contrib.{ ExtraTweaks, ListAdapter }
import org.macroid.contrib.ExtraTweaks.{ TextSize, TextStyle }

import net.routestory.{ R, RouteStoryApp, SettingsActivity }
import net.routestory.explore.{ ExploreActivity, MyStoriesActivity }
import net.routestory.recording.RecordActivity
import org.macroid.Contexts

trait RouteStoryActivity extends FragmentActivity with Contexts[FragmentActivity] {
  lazy val app = getApplication.asInstanceOf[RouteStoryApp]
  lazy val bar = getActionBar
  var drawerToggle: ActionBarDrawerToggle = _

  def displaySize = {
    val pt = new Point
    getWindowManager.getDefaultDisplay.getSize(pt)
    pt.x :: pt.y :: Nil
  }

  override def onOptionsItemSelected(item: MenuItem) =
    Option(drawerToggle).map(_.onOptionsItemSelected(item)).filter(x ⇒ x).getOrElse(super.onOptionsItemSelected(item))

  override def onPostCreate(savedInstanceState: Bundle) {
    super.onPostCreate(savedInstanceState)
    Option(drawerToggle).map(_.syncState())
  }

  def activityProgress =
    w[ProgressBar](null, android.R.attr.progressBarStyleHorizontal) ~>
      lpOf[ViewGroup](MATCH_PARENT, WRAP_CONTENT)

  def drawer(view: View) = {
    def clicker[A <: Activity: ClassTag] = On.click {
      startActivityForResult(new Intent(this, implicitly[ClassTag[A]].runtimeClass), 0)
    }
    // format: OFF
    val data = Seq(
      ("Explore", clicker[ExploreActivity]),
      ("Create a story", clicker[RecordActivity]),
      ("My stories", clicker[MyStoriesActivity]),
      ("Settings", clicker[SettingsActivity])
    )
    val adapter = ListAdapter.text(data)(
      TextSize.medium + TextStyle.boldItalic + padding(all = 10 dp),
      data ⇒ text(data._1) + data._2
    )
    // format: ON
    val layout = l[DrawerLayout](
      view ~> lp(MATCH_PARENT, MATCH_PARENT),
      w[ListView] ~>
        (tweak ~ (_.setAdapter(adapter))) ~>
        lp(240 dp, MATCH_PARENT, Gravity.START) ~>
        ExtraTweaks.Bg.res(R.color.drawer)
    )
    drawerToggle = new ActionBarDrawerToggle(
      this, layout, R.drawable.ic_navigation_drawer,
      R.string.app_name, R.string.app_name
    )
    layout.setDrawerListener(drawerToggle); layout
  }

  def setupSearch(menu: Menu) = {
    val searchManager = getSystemService(Context.SEARCH_SERVICE).asInstanceOf[SearchManager]
    val searchMenuItem = menu.findItem(R.id.search)
    val searchView = searchMenuItem.getActionView.asInstanceOf[SearchView]
    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName))
    searchView.setIconifiedByDefault(false)
    searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener {
      override def onFocusChange(view: View, queryTextFocused: Boolean) {
        if (!queryTextFocused) {
          searchMenuItem.collapseActionView()
        }
      }
    })
  }
}

trait RouteStoryFragment extends Fragment with Contexts[Fragment] {
  lazy val app = getActivity.getApplication.asInstanceOf[RouteStoryApp]

  def displaySize = {
    val pt = new Point
    getActivity.getWindowManager.getDefaultDisplay.getSize(pt)
    pt.x :: pt.y :: Nil
  }
}