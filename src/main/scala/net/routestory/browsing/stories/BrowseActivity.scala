package net.routestory.browsing.stories

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.{ Menu, MenuItem }
import com.applause.android.Applause
import com.applause.android.config.Configuration
import com.google.android.gms.common.{ ConnectionResult, GooglePlayServicesUtil }
import macroid.FullDsl._
import macroid.IdGeneration
import net.routestory.R
import net.routestory.recording.RecordActivity
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }

class BrowseActivity extends RouteStoryActivity with FragmentPaging with IdGeneration {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    // install Google Play Services if needed
    val result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)
    if (result != ConnectionResult.SUCCESS) {
      GooglePlayServicesUtil.getErrorDialog(result, this, 0).show()
    }

    // configure Applause SDK
    //    val applauseConfig = new Configuration.Builder(this)
    //      .withAPIKey("242aebf296488bbedfea375f3b7621b79ab9fc51")
    //      .withServerURL("https://aph.applause.com")
    //      .build()
    //    Applause.startNewSession(this, applauseConfig)

    // set default preferences
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

    // show UI
    bar.setHomeButtonEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)

    setContentView(getUi(drawer(getTabs(
      "My stories" → f[LocalFragment].factory,
      "Stories online" → f[OnlineFragment].pass("number" → 10).factory //,
    //"Popular tags" → f[TagsFragment].factory
    ))))
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.activity_explore, menu)
    //setupSearch(menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.create ⇒
      startActivity(new Intent(this, classOf[RecordActivity])); true
    case _ ⇒
      super[RouteStoryActivity].onOptionsItemSelected(item)
  }
}
