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

class SearchFragment extends StoryFragment with WidgetFragment {
  loaded.success(true)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    new VerticalLinearLayout(ctx) {
      this += new TextView(ctx) {
        setText(R.string.explore_search)
        setTextAppearance(ctx, R.style.ExploreSectionAppearance)
      }
      this += new EditText(ctx) { self ⇒
        setInputType(InputType.TYPE_CLASS_TEXT)
        setEms(10)
        setOnEditorActionListener { (v: TextView, actionId: Int, event: KeyEvent) ⇒
          if (Set(EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_DONE).contains(actionId) ||
            event.getAction == KeyEvent.ACTION_DOWN && event.getKeyCode == KeyEvent.KEYCODE_ENTER) {
            val intent = SIntent[SearchResultsActivity]
            intent.setAction(Intent.ACTION_SEARCH)
            intent.putExtra(SearchManager.QUERY, self.getText.toString)
            startActivityForResult(intent, 0)
            true
          } else {
            false
          }
        }
      }
    }
  }
}
