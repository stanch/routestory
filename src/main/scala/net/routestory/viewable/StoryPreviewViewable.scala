package net.routestory.viewable

import android.content.Intent
import android.support.v7.widget.CardView
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.ViewGroup.LayoutParams._
import android.widget.{ ImageView, LinearLayout, TextView }
import macroid.FullDsl._
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.util.Ui
import macroid.viewable.SlottedFillableViewable
import macroid.{ Tweak, ActivityContext, AppContext }
import net.routestory.R
import net.routestory.browsing.{ SearchActivity, StoryActivity }
import net.routestory.data.StoryPreview
import net.routestory.ui.Styles
import net.routestory.ui.Styles._
import net.routestory.util.BitmapPool.Implicits._
import org.apmem.tools.layouts.FlowLayout
import scala.concurrent.ExecutionContext.Implicits.global

object StoryPreviewViewable extends SlottedFillableViewable[StoryPreview] {
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

  class Slots {
    var title = slot[TextView]
    var authorName = slot[TextView]
    var authorPicture = slot[ImageView]
    var tags = slot[FlowLayout]
  }

  def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val slots = new Slots
    val layout = l[LinearLayout](
      l[CardView](
        l[VerticalLinearLayout](
          w[TextView] <~ wire(slots.title) <~ Styles.title <~ lp[LinearLayout](WRAP_CONTENT, WRAP_CONTENT),
          l[HorizontalLinearLayout](
            w[ImageView] <~ wire(slots.authorPicture) <~ lp[LinearLayout](28 dp, 28 dp) <~ padding(right = 4 dp),
            w[TextView] <~ wire(slots.authorName) <~ Styles.medium
          ),
          l[FlowLayout]() <~ wire(slots.tags)
        ) <~ Styles.p8dding
      ) <~ Styles.card <~ Styles.matchParent
    ) <~ padding(4 dp, 4 dp, 0, 4 dp)
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
    val pictureTweak = story.author.flatMap(_.picture).map(_.map(_.bitmapTweak(28 dp)))
    val fillAuthor = Ui.sequence(
      slots.authorName <~ text(author),
      slots.authorPicture <~ pictureTweak
    )

    // tags
    val fillTags = slots.tags <~ (Some(story.tags).filter(_.nonEmpty) map { tags ⇒
      addViews(tags.map(tag(_, None)), removeOld = true) + show
    } getOrElse {
      hide
    })

    fillTitle ~ fillAuthor ~ fillTags
  }
}