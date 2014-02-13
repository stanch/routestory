package net.routestory.disp

import scala.concurrent.ExecutionContext.Implicits.global

import android.content.Intent
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.View
import android.view.ViewGroup.LayoutParams._
import android.widget.{ ImageView, LinearLayout, TextView }

import org.macroid.FullDsl._
import org.macroid.{ AppContext, ActivityContext }
import org.macroid.contrib.ExtraTweaks._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import org.macroid.viewable.SlottedFillableViewable

import net.routestory.R
import net.routestory.ui.Styles
import net.routestory.ui.Styles._
import net.routestory.model.StoryPreview
import net.routestory.explore.SearchActivity
import net.routestory.util.BitmapUtils
import net.routestory.model.MediaOps._
import net.routestory.needs.BitmapPool

object StoryPreviewViewable {
  def underlined(s: String) = new SpannableString(s) {
    setSpan(new UnderlineSpan, 0, length, 0)
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
}

class StoryPreviewViewable(maxWidth: Int) extends SlottedFillableViewable[StoryPreview] {
  import StoryPreviewViewable._

  class Slots {
    var title = slot[TextView]
    var authorName = slot[TextView]
    var authorPicture = slot[ImageView]
    var tags = slot[LinearLayout]
    var tagRows = slot[LinearLayout]
  }

  def makeSlots(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val slots = new Slots
    val layout = l[VerticalLinearLayout](
      w[TextView] ~> wire(slots.title) ~> Styles.title ~> lp(WRAP_CONTENT, WRAP_CONTENT),
      l[HorizontalLinearLayout](
        w[ImageView] ~> wire(slots.authorPicture) ~> lp(28 dp, 28 dp) ~> padding(right = 4 dp),
        w[TextView] ~> wire(slots.authorName) ~> TextSize.medium
      ) ~> padding(left = storyShift),
      l[HorizontalLinearLayout](
        l[VerticalLinearLayout]() ~> wire(slots.tagRows)
      ) ~> wire(slots.tags) ~> padding(left = storyShift)
    ) ~> padding(0, 8 dp, 0, 8 dp)
    (layout, slots)
  }

  def fillSlots(slots: Slots, story: StoryPreview)(implicit ctx: ActivityContext, appCtx: AppContext) {
    // title
    val title = story.title.filter(!_.isEmpty).toRight(R.string.untitled)
    slots.title ~> text(title) ~> On.click {
      ctx.activity.get.map { a ⇒
        val intent = new Intent(a, classOf[net.routestory.display.DisplayActivity])
        intent.putExtra("id", story.id)
        a.startActivityForResult(intent, 0)
      }
    }

    // author
    val author = story.author.map(_.name).toRight(R.string.me)
    slots.authorName ~> text(author)
    val picture = story.author.flatMap(_.bitmap(28 dp))
    slots.authorPicture ~> picture.map(_.map(Image.bitmap))

    // tags
    slots.tags ~> (Some(story.tags).filter(!_.isEmpty) map { tags ⇒
      fillTags(slots.tagRows, maxWidth, tags.map((_, None)))
      show
    } getOrElse {
      hide
    })
  }
}