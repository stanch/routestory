package net.routestory;

import android.app.Activity
import android.os.Bundle
import org.scaloid.common._
import android.widget.CheckBox
import com.actionbarsherlock.app.SherlockPreferenceActivity

class SettingsActivity extends SherlockPreferenceActivity with SActivity {
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
	}
}
