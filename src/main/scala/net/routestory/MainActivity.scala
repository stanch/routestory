package net.routestory

import org.scaloid.common._
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import android.os.Bundle
import net.routestory.explore.{MyStoriesActivity, ExploreActivity}
import net.routestory.parts.HapticButton
import net.routestory.recording.RecordActivity
import android.preference.PreferenceManager
import net.routestory.parts.StoryActivity
import android.view.{Menu, MenuItem}

class MainActivity extends StoryActivity {
    override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.startup_screen)

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
        
        // map buttons to activities
        List(R.id.recordStory, R.id.explore, R.id.myStories, R.id.myAccount) zip
        List(SIntent[RecordActivity], SIntent[ExploreActivity], SIntent[MyStoriesActivity], SIntent[AccountActivity]) foreach {
            case (id, int) ⇒ find[HapticButton](id).onClick(
      	        startActivityForResult(int, 0)
  	        )
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
