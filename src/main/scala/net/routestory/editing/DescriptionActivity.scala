package net.routestory.editing

import macroid.contrib.LpTweaks

import scala.async.Async.{ async, await }
import scala.concurrent.ExecutionContext.Implicits.global

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.InputType
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams._
import android.widget._

import macroid.Tweak
import macroid.FullDsl._
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.viewable.{ FillableViewable, FillableViewableAdapter }

import net.routestory.R
import net.routestory.ui.{ RouteStoryActivity, Styles }
import net.routestory.ui.Styles._
import macroid.Ui

class DescriptionActivity extends RouteStoryActivity {
  var title: EditText = _
  var description: EditText = _
  var tags: MultiAutoCompleteTextView = _
  var public: CheckBox = _

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    def inputType(t: Int) = Tweak[EditText](_.setInputType(t))

    val view = l[VerticalLinearLayout](
      w[TextView] <~
        text(R.string.title) <~ Styles.header(noPadding = true),
      w[EditText] <~ wire(title) <~ LpTweaks.matchWidth <~
        inputType(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE),

      w[TextView] <~
        text(R.string.description) <~ Styles.header(),
      w[EditText] <~ wire(description) <~ LpTweaks.matchWidth <~
        inputType(InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE),

      w[TextView] <~
        text(R.string.tags) <~ Styles.header(),
      w[MultiAutoCompleteTextView] <~ wire(tags) <~ LpTweaks.matchWidth <~
        inputType(InputType.TYPE_CLASS_TEXT) <~ Tweak[MultiAutoCompleteTextView](_.setHint(R.string.tags_hint)),

      w[CheckBox] <~ wire(public) <~ text(R.string.makeprivate) <~ padding(top = 15 sp),
      w[Button] <~ text(R.string.done) <~ On.click(Ui {
        setResult(0, new Intent("result") {
          putExtra("title", title.getText.toString)
          putExtra("description", description.getText.toString)
          putExtra("tags", tags.getText.toString)
          putExtra("private", public.isChecked)
        })
        finish()
      })
    ) <~ p8dding

    setContentView(getUi(view))

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    public.setChecked(prefs.getBoolean("pref_makeprivate", true))
  }

  override def onStart() = {
    super.onStart()
    async {
      val tagz = await(app.webApi.tags.go).map(_.tag)
      runUi(Ui {
        val adapter = FillableViewableAdapter(tagz)(FillableViewable.text(Tweak.blank, t ⇒ text(t)))
        tags.setAdapter(adapter)
        tags.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer)
      })
    }
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    keyCode match {
      case KeyEvent.KEYCODE_BACK ⇒ false // Alert something
      case _ ⇒ super.onKeyDown(keyCode, event)
    }
  }
}
