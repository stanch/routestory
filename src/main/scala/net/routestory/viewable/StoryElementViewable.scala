package net.routestory.viewable

import java.io.FileInputStream

import android.graphics.{ BitmapFactory, Color }
import android.media.MediaPlayer
import android.support.v7.widget.CardView
import android.view.ViewGroup.LayoutParams._
import android.view.{ Gravity, View }
import android.widget._
import macroid.FullDsl._
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.util.Ui
import macroid.viewable.{ SlottedFillableViewable, Viewable }
import macroid.{ ActivityContext, AppContext, Tweak }
import net.routestory.R
import net.routestory.data.Story
import net.routestory.ui.Styles
import net.routestory.util.BitmapPool.Implicits._
import uk.co.senab.photoview.PhotoViewAttacher

import scala.concurrent.ExecutionContext.Implicits.global

class StoryElementViewable(chapter: Story.Chapter, maxIconSize: Int) extends SlottedFillableViewable[Story.KnownElement] {
  class Slots {
    var timestamp = slot[TextView]
    var imageView = slot[ImageView]
    var textView = slot[TextView]
    var progress = slot[ProgressBar]
  }

  val durationFormat = new java.text.SimpleDateFormat("HH:mm:ss") {
    setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
  }

  val timestampFormat = java.text.DateFormat.getDateTimeInstance

  def resBmp(id: Int)(implicit appCtx: AppContext) =
    Image.bitmap(BitmapFactory.decodeResource(appCtx.get.getResources, id))
  def card(x: Ui[View])(implicit ctx: ActivityContext) =
    l[CardView](x) <~ Styles.card

  override def viewTypeCount = 5
  override def viewType(data: Story.KnownElement) = data match {
    case x: Story.Image ⇒ 0
    case x: Story.TextNote ⇒ 1
    case x: Story.VoiceNote ⇒ 2
    case x: Story.Sound ⇒ 3
    case _ ⇒ 4
  }

  def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val slots = new Slots
    val view = viewType match {
      case 0 ⇒ l[FrameLayout](
        w[ImageView] <~ Image.adjustBounds <~ wire(slots.imageView) <~ hide,
        w[ProgressBar](null, android.R.attr.progressBarStyleLarge) <~ wire(slots.progress) <~
          lp[FrameLayout](WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
      )
      case 1 ⇒ w[TextView] <~ TextSize.sp(22) <~ padding(all = 16 dp) <~ wire(slots.textView)
      case 2 ⇒ l[HorizontalLinearLayout](
        w[ImageView] <~ Image.bitmap(BitmapFactory.decodeResource(appCtx.get.getResources, R.drawable.ic_action_mic)) <~
          Styles.p8dding,
        w[TextView] <~ TextSize.sp(22) <~ wire(slots.textView) <~
          padding(top = 8 dp, bottom = 8 dp)
      )
      case 3 ⇒ l[HorizontalLinearLayout](
        w[ImageView] <~ Image.bitmap(BitmapFactory.decodeResource(appCtx.get.getResources, R.drawable.ic_action_volume_on)) <~
          Styles.p8dding,
        w[TextView] <~ TextSize.sp(22) <~ TextStyle.italic <~ text("ambient sound ") <~
          padding(top = 8 dp, bottom = 8 dp)
      )
      case _ ⇒ w[ImageView]
    }
    val card = l[CardView](l[VerticalLinearLayout](
      w[TextView] <~ Tweak[TextView](_.setTextColor(Color.GRAY)) <~ padding(all = 4 dp) <~ wire(slots.timestamp),
      view
    ))
    (card, slots)
  }

  def fillSlots(slots: Slots, data: Story.KnownElement)(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val ts = slots.timestamp <~ text(timestampFormat.format((chapter.start + data.timestamp) * 1000))
    val ui = data match {
      case x: Story.Image ⇒
        // format: OFF
        (slots.progress <~ show) ~
        (slots.imageView <~~ x.data.map(_.bitmapTweak(maxIconSize))) ~~
        (slots.progress <~~ fadeOut(200)) ~
        (slots.imageView <~ fadeIn(500))
      // format: ON
      case Story.TextNote(_, txt) ⇒
        slots.textView <~ text(txt)
      case x: Story.VoiceNote ⇒
        val duration = x.data map { f ⇒
          val mp = new MediaPlayer
          val fs = new FileInputStream(f)
          mp.setDataSource(fs.getFD)
          mp.prepare()
          val d = mp.getDuration
          mp.release()
          durationFormat.format(d)
        }
        slots.textView <~ duration.map(text)
      case x: Story.Sound ⇒ Ui.nop
      case x: Story.Venue ⇒ Ui.nop
    }
    ts ~ ui
  }
}

class StoryElementDetailedViewable(maxImageSize: Int) extends Viewable[Story.KnownElement] {
  type W = View

  override def layout(data: Story.KnownElement)(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val view = data match {
      case x: Story.Image ⇒
        val bitmapTweak = x.data.map(_.bitmapTweak(maxImageSize) + Tweak[ImageView] { v ⇒
          new PhotoViewAttacher(v).update()
        })
        w[ImageView] <~ Image.adjustBounds <~ Styles.lowProfile <~ bitmapTweak
      case x: Story.TextNote ⇒
        l[ScrollView](w[TextView] <~ text(x.text) <~ TextSize.sp(30) <~ Tweak[TextView](_.setTextColor(Color.WHITE)))
      case x: Story.Audio ⇒
        w[TextView] <~ text("sound here")
      case x: Story.Venue ⇒
        w[TextView] <~ text("venue here")
    }
    view <~ Styles.matchParent
  }
}
