package net.routestory.viewable

import android.content.Intent
import android.support.v7.widget.CardView
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.widget.{ ImageView, LinearLayout, TextView }
import macroid.FullDsl._
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.contrib.{ LpTweaks, TextTweaks }
import macroid.viewable.SlottedListable
import macroid.{ ActivityContext, AppContext, Ui }
import net.routestory.R
import net.routestory.browsing.stories.SearchActivity
import net.routestory.browsing.story.StoryActivity
import net.routestory.data.StoryPreview
import net.routestory.ui.Styles
import net.routestory.util.BitmapPool.Implicits._
import org.apmem.tools.layouts.FlowLayout
import macroid.viewable._

import scala.concurrent.ExecutionContext.Implicits.global

object StoryPreviewListable {
  def underlined(s: String) = new SpannableString(s) {
    setSpan(new UnderlineSpan, 0, length, 0)
  }

  def tag(name: String, size: Option[Double])(implicit ctx: ActivityContext, appCtx: AppContext) =
    w[TextView] <~ Styles.tag <~ text(underlined(name)) <~
      size.map(s ⇒ TextTweaks.size(20 + (s * 20).toInt)) <~
      On.click(Ui {
        ctx.activity.get map { a ⇒
          val intent = new Intent(a, classOf[SearchActivity])
          intent.putExtra("tag", name)
          a.startActivityForResult(intent, 0)
        }
      })

  implicit object storyPreviewListable extends SlottedListable[StoryPreview] {
    class Slots {
      var card = slot[CardView]
      var title = slot[TextView]
      var authorName = slot[TextView]
      var authorPicture = slot[ImageView]
      var tags = slot[FlowLayout]
    }

    def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val layout = l[CardView](
        l[VerticalLinearLayout](
          w[TextView] <~ wire(slots.title) <~ Styles.title <~ LpTweaks.wrapContent,
          l[HorizontalLinearLayout](
            w[ImageView] <~ wire(slots.authorPicture) <~ lp[LinearLayout](28 dp, 28 dp) <~ padding(right = 4 dp),
            w[TextView] <~ wire(slots.authorName) <~ TextTweaks.medium
          ),
          l[FlowLayout]() <~ wire(slots.tags)
        ) <~ Styles.p8dding
      ) <~ wire(slots.card) <~ Styles.card
      (layout, slots)
    }

    def fillSlots(slots: Slots, story: StoryPreview)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      // title
      val title = story.title.filter(!_.isEmpty).toRight(R.string.untitled)
      val fillTitle = slots.title <~ text(title)

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

      // outer click
      val fillOuter = slots.card <~ On.click(Ui {
        ctx.activity.get.map { a ⇒
          val intent = new Intent(a, classOf[StoryActivity]).putExtra("id", story.id)
          a.startActivityForResult(intent, 0)
        }
      })

      fillTitle ~ fillAuthor ~ fillTags ~ fillOuter
    }
  }
}