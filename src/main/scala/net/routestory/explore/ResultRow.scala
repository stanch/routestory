package net.routestory.explore

import scala.collection.JavaConversions._
import org.scaloid.common._
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.{ ViewGroup, LayoutInflater, View }
import android.widget.LinearLayout
import android.widget.TextView
import net.routestory.R
import net.routestory.display.DisplayActivity
import net.routestory.model.StoryResult
import android.view.ViewGroup.LayoutParams
import org.macroid.{ Tweaks, LayoutDsl, BasicViewSearch }
import net.routestory.parts.Tweaks._
import org.macroid.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import ViewGroup.LayoutParams._

object ResultRow {
  def underlined(s: String): SpannableString = {
    val underlined = new SpannableString(s)
    underlined.setSpan(new UnderlineSpan(), 0, underlined.length(), 0)
    underlined
  }

  def fillTags(tagRowsView: LinearLayout, maxwidth: Int, tagz: Array[String], context: Context) {
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
          //context.startActivityForResult(intent, 0)
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

  def getView(_view: View, maxwidth: Int, story: StoryResult, context: Context): View = {
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
      //context.startActivityForResult(intent, 0)
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

object ResultRow2 extends LayoutDsl with Tweaks with BasicViewSearch {
  def underlined(s: String) = new SpannableString(s) {
    setSpan(new UnderlineSpan(), 0, length, 0)
  }

  def fillTags(tagRowsView: LinearLayout, maxWidth: Int, tags: Array[String], activity: Activity)(implicit ctx: Context) {
    tagRowsView.removeAllViews()

    // form rows by accumulating tags that fit in one line
    val rows = tags.foldLeft[(Int, List[List[View]])]((0, Nil :: Nil)) {
      case ((width, row :: prevRows), tag) ⇒

        // create spacer if needed
        val (spacer, spacerWidth) = if (row.isEmpty) (Nil, 0) else {
          val s = w[TextView] ~> mediumText ~> text(", ") ~> measure
          (s :: Nil, s.getMeasuredWidth)
        }

        // create the piece
        val (piece, pieceWidth) = {
          val p = w[TextView] ~> tagAppearance ~> text(underlined(tag)) ~> measure ~> On.click {
            val intent = new Intent(activity, classOf[SearchResultsActivity])
            intent.putExtra("tag", tag)
            activity.startActivityForResult(intent, 0)
          }
          (p :: Nil, p.getMeasuredWidth)
        }

        val occupied = width + spacerWidth + pieceWidth
        if (occupied > maxWidth && !row.isEmpty) {
          // create a new line
          (pieceWidth, piece :: (spacer ::: row) :: prevRows)
        } else {
          // fit on the same line
          (occupied, (piece ::: spacer ::: row) :: prevRows)
        }
    }

    // add to layout
    rows._2.foreach { row ⇒
      val r = l[HorizontalLinearLayout]() ~> lpOf[LinearLayout](MATCH_PARENT, WRAP_CONTENT) ~> addViewsReverse(row)
      tagRowsView.addView(r, 0)
    }
  }

  def getView(_view: View, maxWidth: Int, story: StoryResult, activity: Activity)(implicit ctx: Context) = {
    // init the view
    val view = Option(_view) getOrElse {
      l[VerticalLinearLayout](
        w[TextView] ~> id(Id.storyTitle),
        l[HorizontalLinearLayout](
          w[TextView] ~> text(R.string.by) ~> mediumText,
          w[TextView] ~> id(Id.storyAuthor) ~> mediumText
        ),
        l[HorizontalLinearLayout](
          w[TextView] ~> id(Id.tagged) ~> text(R.string.tagged) ~> mediumText,
          l[VerticalLinearLayout]() ~> id(Id.storyTagRows)
        ) ~> id(Id.storyTags)
      )
    }

    // title
    val title = Option(story.title).filter(_.length > 0).toRight(R.string.untitled)
    findView[TextView](view, Id.storyTitle) ~> text(title) ~> titleAppearance ~> On.click {
      val intent = new Intent(activity, classOf[DisplayActivity])
      intent.putExtra("id", story.id)
      activity.startActivityForResult(intent, 0)
    }

    // author
    val author = Option(story.author).map(_.name).toRight(R.string.me)
    findView[TextView](view, Id.storyAuthor) ~> text(author)

    // tags
    findView[View](view, Id.storyTags) ~> (Option(story.tags).filter(_.length > 0).filterNot(t ⇒ t.length == 1 && t(0) == "") map { tags ⇒
      val tagged = findView[TextView](view, Id.tagged) ~> (_.measure(0, 0))
      val tagRows = findView[LinearLayout](view, Id.storyTagRows)
      fillTags(tagRows, maxWidth - tagged.getMeasuredWidth * 4 / 3, tags, activity)
      show
    } getOrElse {
      hide
    })

    view
  }
}