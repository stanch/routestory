package net.routestory.explore

import android.os.Bundle
import net.routestory.parts.{ FragmentPaging, RouteStoryActivity }
import com.google.android.gms.common.{ ConnectionResult, GooglePlayServicesUtil }
import android.preference.PreferenceManager
import net.routestory.R
import android.view.{ MenuItem, Menu }
import android.content.Intent
import net.routestory.recording.RecordActivity

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
      "Latest" → f[LatestFragment].pass("number" → 10).factory,
      "Popular tags" → f[TagsFragment].factory
    )))
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.activity_explore, menu)
    setupSearch(menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.create ⇒
      startActivity(new Intent(this, classOf[RecordActivity])); true
    case _ ⇒
      super[RouteStoryActivity].onOptionsItemSelected(item)
  }
}
