package net.routestory.recording;

import org.scaloid.common._
import com.actionbarsherlock.app.SherlockFragmentActivity
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import net.routestory.R
import net.routestory.parts.StoryActivity
import android.view.View


class DescriptionActivity extends SherlockFragmentActivity with StoryActivity {
	override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_description)
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        findView[CheckBox](R.id.checkBox1).setChecked(prefs.getBoolean("pref_makeprivate", true))
        
        findView[Button](R.id.button1) setOnClickListener { v: View =>
        	val data = new Intent("result") {
        		putExtra("title", findView[EditText](R.id.editText1).getText().toString())
        		putExtra("description", findView[EditText](R.id.editText2).getText().toString())
        		putExtra("tags", findView[EditText](R.id.editText3).getText().toString())
        		putExtra("private", findView[CheckBox](R.id.checkBox1).isChecked())
        	}
			setResult(0, data)
			finish()
        }
    }
    
    override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    	keyCode match {
    		case KeyEvent.KEYCODE_BACK => false // Alert something
    		case _ => super.onKeyDown(keyCode, event)
    	}
    }
}
