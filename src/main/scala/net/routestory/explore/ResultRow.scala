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
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import ViewGroup.LayoutParams._

object ResultRow extends LayoutDsl with Tweaks with BasicViewSearch {
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
          val s = w[TextView] ~> TextSize.medium ~> text(", ") ~> measure
          (s :: Nil, s.getMeasuredWidth)
        }

        // create the piece
        val (piece, pieceWidth) = {
          val p = w[TextView] ~> tagStyle ~> text(underlined(tag)) ~> measure ~> On.click {
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

  def getView(_view: Option[View], maxWidth: Int, story: StoryResult, activity: Activity)(implicit ctx: Context) = {
    // init the view
    val view = _view getOrElse {
      l[VerticalLinearLayout](
        w[TextView] ~> id(Id.storyTitle) ~> titleStyle ~> lp(WRAP_CONTENT, WRAP_CONTENT),
        l[HorizontalLinearLayout](
          w[TextView] ~> text(R.string.by) ~> captionStyle,
          w[TextView] ~> id(Id.storyAuthor) ~> TextSize.medium
        ),
        l[HorizontalLinearLayout](
          w[TextView] ~> id(Id.tagged) ~> text(R.string.tagged) ~> captionStyle,
          l[VerticalLinearLayout]() ~> id(Id.storyTagRows)
        ) ~> id(Id.storyTags)
      ) ~> (_.setPadding(0, 8 dip, 0, 8 dip))
    }

    // title
    val title = Option(story.title).filter(_.length > 0).toRight(R.string.untitled)
    findView[TextView](view, Id.storyTitle) ~> text(title) ~> On.click {
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