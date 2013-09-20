package net.routestory.explore

import net.routestory.parts.{ WidgetFragment, StoryFragment }
import android.view.{ KeyEvent, View, ViewGroup, LayoutInflater }
import android.os.Bundle
import android.widget.{ EditText, TextView }
import net.routestory.R
import org.scaloid.common._
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.content.Intent
import android.app.SearchManager
import net.routestory.parts.Implicits._
import net.routestory.parts.Tweaks._
import org.macroid.Layouts.VerticalLinearLayout

class SearchFragment extends StoryFragment with WidgetFragment {
  loaded.success(true)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[VerticalLinearLayout](
      w[TextView] ~> text(R.string.explore_search) ~> headerStyle,
      w[EditText] ~> FuncOn.editorAction { (v: TextView, actionId: Int, event: KeyEvent) ⇒
        if (Set(EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_DONE).contains(actionId) ||
          event.getAction == KeyEvent.ACTION_DOWN && event.getKeyCode == KeyEvent.KEYCODE_ENTER) {
          val intent = SIntent[SearchResultsActivity]
          intent.setAction(Intent.ACTION_SEARCH)
          intent.putExtra(SearchManager.QUERY, v.getText.toString)
          startActivityForResult(intent, 0)
          true
        } else {
          false
        }
      } ~> { x ⇒
        x.setInputType(InputType.TYPE_CLASS_TEXT)
        x.setEms(10)
      }
    )
  }
}
