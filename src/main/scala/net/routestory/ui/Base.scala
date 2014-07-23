package net.routestory.ui

import android.app.{ Activity, SearchManager }
import android.content.{ Context, Intent }
import android.graphics.{ Color, Point }
import android.os.Bundle
import android.support.v4.app.{ ActionBarDrawerToggle, Fragment, FragmentActivity }
import android.support.v4.widget.DrawerLayout
import android.view.ViewGroup.LayoutParams._
import android.view._
import android.widget.{ ListView, ProgressBar, SearchView }
import macroid.FullDsl._
import macroid.contrib.{ LpTweaks, BgTweaks, ListTweaks, TextTweaks }
import macroid.Ui
import macroid.viewable.{ FillableViewable, FillableViewableAdapter }
import macroid.{ Contexts, Tweak }
import net.routestory.browsing.stories.ExploreActivity
import net.routestory.recording.RecordActivity
import net.routestory.{ R, RouteStoryApp }

import scala.reflect.ClassTag

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
    w[ProgressBar](null, android.R.attr.progressBarStyleHorizontal) <~
      BgTweaks.color(Color.BLACK) <~
      LpTweaks.matchWidth

  def drawer(view: Ui[View]) = {
    def clicker[A <: Activity: ClassTag] = On.click(Ui {
      startActivityForResult(new Intent(this, implicitly[ClassTag[A]].runtimeClass), 0)
    })
    // format: OFF
    val data = Seq(
      ("Explore", clicker[ExploreActivity]),
      ("Create a story", clicker[RecordActivity])
    )
    val adapter = FillableViewableAdapter(data)(FillableViewable.text(
      TextTweaks.medium + TextTweaks.boldItalic + padding(all = 10 dp),
      data ⇒ text(data._1) + data._2
    ))
    // format: ON
    val layout = l[DrawerLayout](
      view <~ LpTweaks.matchParent,
      w[ListView] <~
        ListTweaks.adapter(adapter) <~
        lp[DrawerLayout](240 dp, MATCH_PARENT, Gravity.START) <~
        BgTweaks.res(R.color.drawer)
    )
    layout.flatMap { lay ⇒
      drawerToggle = new ActionBarDrawerToggle(
        this, lay, R.drawable.ic_navigation_drawer,
        R.string.app_name, R.string.app_name
      )
      lay <~ Tweak[DrawerLayout](_.setDrawerListener(drawerToggle))
    }
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