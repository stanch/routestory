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
import org.macroid.Transforms._

class SearchFragment extends StoryFragment with WidgetFragment {
  loaded.success(true)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[VerticalLinearLayout](
      w[TextView] ~> text(R.string.explore_search) ~> (_.setTextAppearance(ctx, R.style.ExploreSectionAppearance)),
      w[EditText] ~> { x ⇒
        x.setInputType(InputType.TYPE_CLASS_TEXT)
        x.setEms(10)
        x.setOnEditorActionListener { (v: TextView, actionId: Int, event: KeyEvent) ⇒
          if (Set(EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_DONE).contains(actionId) ||
            event.getAction == KeyEvent.ACTION_DOWN && event.getKeyCode == KeyEvent.KEYCODE_ENTER) {
            val intent = SIntent[SearchResultsActivity]
            intent.setAction(Intent.ACTION_SEARCH)
            intent.putExtra(SearchManager.QUERY, x.getText.toString)
            startActivityForResult(intent, 0)
            true
          } else {
            false
          }
        }
      }
    )
  }
}
