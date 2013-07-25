package net.routestory.recording

import org.scaloid.common._
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.widget._
import net.routestory.R
import net.routestory.parts.StoryActivity
import android.view.View
import akka.dataflow._
import org.ektorp.ViewQuery
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

class DescriptionActivity extends StoryActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_description)

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    findView[CheckBox](R.id.checkBox1).setChecked(prefs.getBoolean("pref_makeprivate", true))

    findView[Button](R.id.button1) setOnClickListener { v: View ⇒
      val data = new Intent("result") {
        putExtra("title", findView[EditText](R.id.editText1).getText.toString)
        putExtra("description", findView[EditText](R.id.editText2).getText.toString)
        putExtra("tags", findView[EditText](R.id.editText3).getText.toString)
        putExtra("private", findView[CheckBox](R.id.checkBox1).isChecked)
      }
      setResult(0, data)
      finish()
    }
  }

  override def onStart() {
    super.onStart()
    flow {
      val query = new ViewQuery().designDocId("_design/Story").viewName("tags").group(true)
      val tags = await(app.getPlainQueryResults(remote = true, query))
      val tagArray = tags.getRows.map(_.getKey).toArray
      switchToUiThread()
      val adapter = new ArrayAdapter[String](ctx, android.R.layout.simple_dropdown_item_1line, tagArray)
      val tagsField = findView[MultiAutoCompleteTextView](R.id.editText3)
      tagsField.setAdapter(adapter)
      tagsField.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer)
    }
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    keyCode match {
      case KeyEvent.KEYCODE_BACK ⇒ false // Alert something
      case _ ⇒ super.onKeyDown(keyCode, event)
    }
  }
}
