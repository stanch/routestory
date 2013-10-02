package net.routestory.explore

import android.os.Bundle
import net.routestory.parts.{ FragmentPaging, StoryActivity }
import com.google.android.gms.common.{ ConnectionResult, GooglePlayServicesUtil }
import android.preference.PreferenceManager
import net.routestory.R

class ExploreActivity extends StoryActivity with FragmentPaging {
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
}
