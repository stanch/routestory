package net.routestory.recording

import org.scaloid.common._
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.{ ViewGroup, KeyEvent, View }
import android.widget._
import net.routestory.R
import net.routestory.parts.{ HapticButton, RouteStoryActivity }
import org.ektorp.ViewQuery
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import net.routestory.parts.Styles._
import ViewGroup.LayoutParams._
import android.text.InputType
import scala.async.Async.{ async, await }

class DescriptionActivity extends RouteStoryActivity {
  var title: EditText = _
  var description: EditText = _
  var tags: MultiAutoCompleteTextView = _
  var public: CheckBox = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    val view = l[VerticalLinearLayout](
      w[TextView] ~>
        text(R.string.title) ~> headerStyle(noPadding = true),
      w[EditText] ~> wire(title) ~> lp(MATCH_PARENT, WRAP_CONTENT) ~>
        (_.setInputType(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE)),

      w[TextView] ~>
        text(R.string.description) ~> headerStyle(),
      w[EditText] ~> wire(description) ~> lp(MATCH_PARENT, WRAP_CONTENT) ~>
        (_.setInputType(InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)),

      w[TextView] ~>
        text(R.string.tags) ~> headerStyle(),
      w[MultiAutoCompleteTextView] ~> wire(tags) ~> lp(MATCH_PARENT, WRAP_CONTENT) ~>
        (_.setInputType(InputType.TYPE_CLASS_TEXT)) ~> (_.setHint(R.string.tags_hint)),

      w[CheckBox] ~> wire(public) ~> text(R.string.makeprivate) ~> padding(top = (15 sp)),
      w[HapticButton] ~> text(R.string.done) ~> On.click {
        setResult(0, new Intent("result") {
          putExtra("title", title.getText.toString)
          putExtra("description", description.getText.toString)
          putExtra("tags", tags.getText.toString)
          putExtra("private", public.isChecked)
        })
        finish()
      }
    ) ~> p8dding

    setContentView(view)

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    public.setChecked(prefs.getBoolean("pref_makeprivate", true))
  }

  override def onStart() {
    super.onStart()
    async {
      val query = new ViewQuery().designDocId("_design/Story").viewName("tags").group(true)
      val tagz = await(app.getPlainQueryResults(remote = true, query))
      val tagArray = tagz.getRows.map(_.getKey).toArray
      Ui {
        val adapter = new ArrayAdapter[String](ctx, android.R.layout.simple_dropdown_item_1line, tagArray)
        tags.setAdapter(adapter)
        tags.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer)
      }
    }
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    keyCode match {
      case KeyEvent.KEYCODE_BACK ⇒ false // Alert something
      case _ ⇒ super.onKeyDown(keyCode, event)
    }
  }
}
