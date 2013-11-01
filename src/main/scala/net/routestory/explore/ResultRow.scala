package net.routestory.explore

import scala.collection.JavaConversions._
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.{ ViewGroup, View }
import android.widget.LinearLayout
import android.widget.TextView
import net.routestory.R
import net.routestory.display.DisplayActivity
import net.routestory.model.StoryResult
import org.macroid.{ MediaQueries, Tweaks, LayoutDsl, BasicViewSearch }
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import ViewGroup.LayoutParams._
import net.routestory.parts.Styles

object ResultRow extends LayoutDsl with MediaQueries with Tweaks with BasicViewSearch {
  def underlined(s: String) = new SpannableString(s) {
    setSpan(new UnderlineSpan(), 0, length, 0)
  }

  def fillTags(tagRowsView: Option[LinearLayout], maxWidth: Int, tags: Array[(String, Option[Double])], activity: Activity)(implicit ctx: Context) {
    // form rows by accumulating tags that fit in one line
    val (_, rows) = tags.foldLeft[(Int, List[List[View]])]((0, Nil :: Nil)) {
      case ((width, row :: prevRows), tag) ⇒

        // create spacer if needed
        val (spacer, spacerWidth) = if (row.isEmpty) (Nil, 0) else {
          val s = w[TextView] ~> TextSize.medium ~> text(", ") ~> measure
          (s :: Nil, s.getMeasuredWidth)
        }

        // create the piece
        val (piece, pieceWidth) = {
          val p = w[TextView] ~> Styles.tag ~> text(underlined(tag._1)) ~>
            tag._2.map(s ⇒ TextSize.sp(20 + (s * 20).toInt)) ~>
            measure ~> On.click {
              val intent = new Intent(activity, classOf[SearchActivity])
              intent.putExtra("tag", tag._1)
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
    tagRowsView ~> addViewsReverse(rows.map(row ⇒ l[HorizontalLinearLayout]() ~> addViewsReverse(row)), removeOld = true)
  }

  def getView(_view: Option[View], maxWidth: Int, story: StoryResult, activity: Activity)(implicit ctx: Context) = {
    // init the view
    val view = _view getOrElse {
      l[VerticalLinearLayout](
        w[TextView] ~> id(Id.storyTitle) ~> Styles.title ~> lp(WRAP_CONTENT, WRAP_CONTENT),
        l[HorizontalLinearLayout](
          w[TextView] ~> text(R.string.by) ~> Styles.caption,
          w[TextView] ~> id(Id.storyAuthor) ~> TextSize.medium
        ),
        l[HorizontalLinearLayout](
          l[VerticalLinearLayout]() ~> id(Id.storyTagRows)
        ) ~> id(Id.storyTags) ~> padding(left = storyShift)
      ) ~> padding(0, 8 dp, 0, 8 dp)
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
      val tagRows = findView[LinearLayout](view, Id.storyTagRows)
      fillTags(tagRows, maxWidth, tags.map((_, None)), activity)
      show
    } getOrElse {
      hide
    })

    view
  }
}