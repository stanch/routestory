package net.routestory.explore

import android.os.Bundle
import net.routestory.parts.{ FragmentPaging, RouteStoryActivity }
import com.google.android.gms.common.{ ConnectionResult, GooglePlayServicesUtil }
import android.preference.PreferenceManager
import net.routestory.R
import android.view.{ MenuItem, View, Menu }
import android.content.{ Intent, Context }
import android.app.SearchManager
import android.widget.SearchView
import net.routestory.recording.RecordActivity
import android.util.Log
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.explore2.{ TagsFragment, LatestFragment }

class ExploreActivity extends RouteStoryActivity with FragmentPaging {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    // install Google Play Services if needed
    val result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)
    if (result != ConnectionResult.SUCCESS) {
      GooglePlayServicesUtil.getErrorDialog(result, this, 0).show()
    }

    // set default preferences
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

    // show UI
    bar.setHomeButtonEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(drawer(getTabs(
      "Latest" → ff[LatestFragment]("number" → 10),
      "Popular tags" → ff[TagsFragment]()
    )))
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.activity_explore, menu)
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
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.create ⇒
        startActivity(new Intent(this, classOf[RecordActivity])); true
      case _ ⇒ super[RouteStoryActivity].onOptionsItemSelected(item)
    }
  }
}
