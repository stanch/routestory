package net.routestory.explore

import scala.collection.JavaConversions._
import org.scaloid.common._
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import net.routestory.R
import net.routestory.display.DisplayActivity
import net.routestory.model.StoryResult
import android.view.ViewGroup.LayoutParams

object ResultRow {
  def underlined(s: String): SpannableString = {
    val underlined = new SpannableString(s)
    underlined.setSpan(new UnderlineSpan(), 0, underlined.length(), 0)
    underlined
  }

  def fillTags(tagRowsView: LinearLayout, maxwidth: Int, tagz: Array[String], context: Activity) {
    var width = -1
    tagRowsView.removeAllViews()
    var row: LinearLayout = null
    for (i ← tagz.indices) {
      if (i > 0) {
        val spacer = new TextView(context) {
          setTextAppearance(context, android.R.attr.textAppearanceMedium)
          setText(", ")
          measure(0, 0)
        }
        width -= spacer.getMeasuredWidth
        row.addView(spacer)
      }
      val tag = new TextView(context) {
        setTextAppearance(context, R.style.TagAppearance)
        setText(underlined(tagz(i)))
        setOnClickListener { v: View ⇒
          val intent = new Intent(context, classOf[SearchResultsActivity])
          intent.putExtra("tag", tagz(i))
          context.startActivityForResult(intent, 0)
        }
        measure(0, 0)
      }
      width -= tag.getMeasuredWidth
      if (width < 0) {
        row = new LinearLayout(context) {
          setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
          setOrientation(LinearLayout.HORIZONTAL)
        }
        tagRowsView.addView(row)
        width = maxwidth - tag.getMeasuredWidth
      }
      row.addView(tag)
    }
  }

  def getView(_view: View, maxwidth: Int, story: StoryResult, context: Activity): View = {
    val view = Option(_view) getOrElse {
      val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      inflater.inflate(R.layout.storylist_row, null)
    }

    // title
    val title = view.findViewById(R.id.storyTitle).asInstanceOf[TextView]
    title.setText(if (story.title != null && story.title.length() > 0) story.title else context.getResources.getString(R.string.untitled));
    title.setOnClickListener { v: View ⇒
      val intent = new Intent(context, classOf[DisplayActivity])
      intent.putExtra("id", story.id)
      context.startActivityForResult(intent, 0)
    }

    // tags
    val tags = view.findViewById(R.id.storyTags).asInstanceOf[LinearLayout]
    val tagRows = view.findViewById(R.id.storyTagRows).asInstanceOf[LinearLayout]
    val tagged = view.findViewById(R.id.tagged).asInstanceOf[TextView]
    tagged.measure(0, 0)
    if (story.tags != null && story.tags.length > 0 && !(story.tags.length == 1 && story.tags(0).equals(""))) {
      fillTags(tagRows, maxwidth - tagged.getMeasuredWidth * 4 / 3, story.tags, context)
      tags.setVisibility(View.VISIBLE)
    } else {
      tags.setVisibility(View.GONE)
    }

    // author
    if (story.author != null) {
      view.findViewById(R.id.storyAuthor).asInstanceOf[TextView].setText(story.author.name)
    } else {
      view.findViewById(R.id.storyAuthor).asInstanceOf[TextView].setText(R.string.me)
    }
    view
  }
}
