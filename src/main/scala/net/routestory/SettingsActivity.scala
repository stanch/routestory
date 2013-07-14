package net.routestory

import android.os.Bundle
import android.preference.PreferenceActivity

class SettingsActivity extends PreferenceActivity {
    override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
    }
}
