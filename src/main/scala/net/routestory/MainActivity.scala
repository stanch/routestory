package net.routestory

import org.scaloid.common._
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import android.os.Bundle
import net.routestory.explore.{ MyStoriesActivity, ExploreActivity }
import net.routestory.parts.HapticButton
import net.routestory.recording.RecordActivity
import android.preference.PreferenceManager
import net.routestory.parts.StoryActivity
import android.view.{ Gravity, ViewGroup, Menu, MenuItem }
import com.google.android.apps.iosched.ui.widget.DashboardLayout
import net.routestory.parts.Styles._
import android.app.Activity
import org.macroid.util.Thunk
import android.view.ViewGroup.LayoutParams._
import android.content.Intent
import scala.reflect.ClassTag
import android.support.v4.widget.DrawerLayout
import android.widget.ListView

class MainActivity extends StoryActivity {

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    def clicker[A <: Activity: ClassTag] = Thunk {
      startActivityForResult(new Intent(this, implicitly[ClassTag[A]].runtimeClass), 0)
    }
    // format: OFF
    val buttons = List(
      (R.drawable.record2, clicker[RecordActivity]),
      (R.drawable.explore2, clicker[ExploreActivity]),
      (R.drawable.my_stories2, clicker[MyStoriesActivity]),
      (R.drawable.profile2, clicker[AccountActivity])
    ) map { case (b, c) ⇒
      w[HapticButton] ~> bg(b) ~> ThunkOn.click(c)
    }
    // format: ON

    var things: ListView = null

    setContentView(
      l[DrawerLayout](
        l[DashboardLayout]() ~> bg(R.drawable.startup_screen_gradient) ~> addViews(buttons) ~> lp(MATCH_PARENT, MATCH_PARENT),
        w[ListView] ~> lp(240 dip, MATCH_PARENT, Gravity.START) ~> wire(things)
      )
    )
    things.setAdapter(new SArrayAdapter(Array("FOO", "BAR")))

    // install Google Play Services if needed
    val result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)
    if (result != ConnectionResult.SUCCESS) {
      GooglePlayServicesUtil.getErrorDialog(result, this, 0).show()
    }

    // set default preferences
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

    // show the tutorial on first run
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    if (!prefs.getBoolean("pref_tutorialshown", false)) {
      //startActivityForResult(SIntent[TutorialActivity], 0);
    }
  }

  override def onStart() {
    super.onStart()
    app.sync()
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.activity_main, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    vibrator.vibrate(10)
    item.getItemId match {
      case R.id.Settings ⇒ startActivityForResult(SIntent[SettingsActivity], 0)
      case _ ⇒ super.onOptionsItemSelected(item)
    }
    true
  }
}
