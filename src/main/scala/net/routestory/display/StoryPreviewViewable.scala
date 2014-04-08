package net.routestory.display

import scala.concurrent.ExecutionContext.Implicits.global

import android.content.Intent
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.View
import android.view.ViewGroup.LayoutParams._
import android.widget.{ ImageView, LinearLayout, TextView }

import macroid.FullDsl._
import macroid.{ AppContext, ActivityContext }
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.viewable.SlottedFillableViewable

import net.routestory.R
import net.routestory.ui.Styles
import net.routestory.ui.Styles._
import net.routestory.model.StoryPreview
import net.routestory.browsing.{ StoryActivity, SearchActivity }
import net.routestory.util.BitmapUtils
import net.routestory.model.MediaOps._
import net.routestory.needs.BitmapPool
import macroid.util.Ui
import org.apmem.tools.layouts.FlowLayout

object StoryPreviewViewable {
  def underlined(s: String) = new SpannableString(s) {
    setSpan(new UnderlineSpan, 0, length, 0)
  }

  def tag(name: String, size: Option[Double])(implicit ctx: ActivityContext, appCtx: AppContext) =
    w[TextView] <~ Styles.tag <~ text(underlined(name)) <~
      size.map(s ⇒ TextSize.sp(20 + (s * 20).toInt)) <~
      On.click(Ui {
        ctx.activity.get map { a ⇒
          val intent = new Intent(a, classOf[SearchActivity])
          intent.putExtra("tag", name)
          a.startActivityForResult(intent, 0)
        }
      })
}

class StoryPreviewViewable(maxWidth: Int) extends SlottedFillableViewable[StoryPreview] {
  import StoryPreviewViewable._

  class Slots {
    var title = slot[TextView]
    var authorName = slot[TextView]
    var authorPicture = slot[ImageView]
    var tags = slot[FlowLayout]
  }

  def makeSlots(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val slots = new Slots
    val layout = l[VerticalLinearLayout](
      w[TextView] <~ wire(slots.title) <~ Styles.title <~ lp[LinearLayout](WRAP_CONTENT, WRAP_CONTENT),
      l[HorizontalLinearLayout](
        w[ImageView] <~ wire(slots.authorPicture) <~ lp[LinearLayout](28 dp, 28 dp) <~ padding(right = 4 dp),
        w[TextView] <~ wire(slots.authorName) <~ TextSize.medium
      ) <~ padding(left = storyShift),
      l[FlowLayout]() <~ wire(slots.tags) <~ padding(left = storyShift)
    ) <~ padding(0, 8 dp, 0, 8 dp)
    (layout, slots)
  }

  def fillSlots(slots: Slots, story: StoryPreview)(implicit ctx: ActivityContext, appCtx: AppContext) = {
    // title
    val title = story.title.filter(!_.isEmpty).toRight(R.string.untitled)
    val fillTitle = slots.title <~ text(title) <~ On.click(Ui {
      ctx.activity.get.map { a ⇒
        val intent = new Intent(a, classOf[StoryActivity])
        intent.putExtra("id", story.id)
        a.startActivityForResult(intent, 0)
      }
    })

    // author
    val author = story.author.map(_.name).toRight(R.string.me)
    val picture = story.author.flatMap(_.bitmap(28 dp))
    val fillAuthor = Ui.sequence(
      slots.authorName <~ text(author),
      slots.authorPicture <~ picture.map(_.map(Image.bitmap))
    )

    // tags
    val fillTags = slots.tags <~ (Some(story.tags).filter(!_.isEmpty) map { tags ⇒
      addViews(tags.map(tag(_, None)), removeOld = true) + show
    } getOrElse {
      hide
    })

    Ui.sequence(fillTitle, fillAuthor, fillTags)
  }
}