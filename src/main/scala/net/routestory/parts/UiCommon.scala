package net.routestory.parts

import android.support.v4.app.{ ActionBarDrawerToggle, Fragment, FragmentActivity }
import net.routestory.{ SettingsActivity, R, RouteStoryApp }
import org.macroid._
import android.view._
import android.content.{ Context, Intent }
import android.support.v4.widget.DrawerLayout
import android.widget.{ SearchView, ProgressBar, ListView }
import android.view.ViewGroup.LayoutParams._
import android.app.{ SearchManager, Activity }
import scala.reflect.ClassTag
import org.macroid.contrib.{ ExtraTweaks, ListAdapter }
import net.routestory.recording.RecordActivity
import android.os.Bundle
import org.macroid.contrib.ExtraTweaks.{ TextSize, TextStyle }
import net.routestory.explore.{ MyStoriesActivity, ExploreActivity }

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

trait RouteStoryActivity extends FragmentActivity with FullDslActivity with Toasts with FirstEveryStart {
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
        (_.setAdapter(adapter)) ~>
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

trait RouteStoryFragment extends Fragment with FullDslFragment with Toasts with FirstEveryStart {
  lazy val app = getActivity.getApplication.asInstanceOf[RouteStoryApp]

  override def onStart() {
    super.onStart()
    firstOrEvery()
  }
}