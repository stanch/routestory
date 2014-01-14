package net.routestory.explore

import android.content.Intent
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.View
import android.view.ViewGroup.LayoutParams._
import android.widget.{ LinearLayout, TextView }

import org.macroid.FullDsl._
import org.macroid.{ IdGeneration, AppContext, ActivityContext }
import org.macroid.contrib.ExtraTweaks._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }

import net.routestory.R
import net.routestory.ui.Styles
import net.routestory.ui.Styles._
import net.routestory.model.StoryPreview

object PreviewRow extends IdGeneration {
  def underlined(s: String) = new SpannableString(s) {
    setSpan(new UnderlineSpan(), 0, length, 0)
  }

  def fillTags(tagRowsView: Option[LinearLayout], maxWidth: Int, tags: List[(String, Option[Double])])(implicit ctx: ActivityContext, appCtx: AppContext) {
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
              ctx.activity.get map { a ⇒
                val intent = new Intent(a, classOf[SearchActivity])
                intent.putExtra("tag", tag._1)
                a.startActivityForResult(intent, 0)
              }
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

  def makeView(implicit ctx: ActivityContext, appCtx: AppContext) = l[VerticalLinearLayout](
    w[TextView] ~> id(Id.storyTitle) ~> Styles.title ~> lp(WRAP_CONTENT, WRAP_CONTENT),
    l[HorizontalLinearLayout](
      w[TextView] ~> text(R.string.by) ~> Styles.caption,
      w[TextView] ~> id(Id.storyAuthor) ~> TextSize.medium
    ),
    l[HorizontalLinearLayout](
      l[VerticalLinearLayout]() ~> id(Id.storyTagRows)
    ) ~> id(Id.storyTags) ~> padding(left = storyShift)
  ) ~> padding(0, 8 dp, 0, 8 dp)

  def fillView(view: View, maxWidth: Int, story: StoryPreview)(implicit ctx: ActivityContext, appCtx: AppContext) {
    // title
    val title = story.title.filter(!_.isEmpty).toRight(R.string.untitled)
    view.find[TextView](Id.storyTitle) ~> text(title) ~> On.click {
      ctx.activity.get.map { a ⇒
        val intent = new Intent(a, classOf[net.routestory.display.DisplayActivity])
        intent.putExtra("id", story.id)
        a.startActivityForResult(intent, 0)
      }
    }

    // author
    val author = story.author.map(_.name).toRight(R.string.me)
    view.find[TextView](Id.storyAuthor) ~> text(author)

    // tags
    view.find[View](Id.storyTags) ~> (Some(story.tags).filter(!_.isEmpty) map { tags ⇒
      val tagRows = view.find[LinearLayout](Id.storyTagRows)
      fillTags(tagRows, maxWidth, tags.map((_, None)))
      show
    } getOrElse {
      hide
    })
  }
}