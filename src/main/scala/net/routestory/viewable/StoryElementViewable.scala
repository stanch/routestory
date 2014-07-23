package net.routestory.viewable

import java.io.FileInputStream

import android.graphics.{ BitmapFactory, Color }
import android.media.MediaPlayer
import android.support.v7.widget.CardView
import android.view.ViewGroup.LayoutParams._
import android.view.{ Gravity, View }
import android.widget._
import macroid.FullDsl._
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.contrib.{ LpTweaks, ImageTweaks, TextTweaks }
import macroid.Ui
import macroid.viewable.{ SlottedFillableViewable, Viewable }
import macroid.{ ActivityContext, AppContext, Tweak }
import net.routestory.R
import net.routestory.data.Story
import net.routestory.ui.Styles
import net.routestory.util.BitmapPool.Implicits._
import uk.co.senab.photoview.PhotoViewAttacher

import scala.concurrent.ExecutionContext.Implicits.global

class StoryElementViewable(maxIconSize: Int) extends SlottedFillableViewable[Story.KnownElement] {
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
    ImageTweaks.bitmap(BitmapFactory.decodeResource(appCtx.get.getResources, id))

  override def viewTypeCount = 6
  override def viewType(data: Story.KnownElement) = data match {
    case x: Story.Image ⇒ 0
    case x: Story.TextNote ⇒ 1
    case x: Story.VoiceNote ⇒ 2
    case x: Story.Sound ⇒ 3
    case x: Story.FoursquareVenue ⇒ 4
    case _ ⇒ 5
  }

  def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val slots = new Slots
    val view = viewType match {
      case 0 ⇒
        l[FrameLayout](
          w[ImageView] <~ wire(slots.imageView) <~
            ImageTweaks.adjustBounds <~ padding(bottom = 4 dp) <~ hide,
          w[ProgressBar](null, android.R.attr.progressBarStyleLarge) <~ wire(slots.progress) <~
            lp[FrameLayout](WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
        )

      case 1 ⇒
        w[TextView] <~ wire(slots.textView) <~
          TextTweaks.large <~ padding(all = 16 dp)

      case 2 ⇒
        l[HorizontalLinearLayout](
          w[ImageView] <~ ImageTweaks.res(R.drawable.ic_action_mic) <~
            Styles.p8dding,
          w[TextView] <~ wire(slots.textView) <~
            TextTweaks.large <~ padding(top = 8 dp, bottom = 8 dp)
        )

      case 3 ⇒
        l[HorizontalLinearLayout](
          w[ImageView] <~ ImageTweaks.res(R.drawable.ic_action_volume_on) <~
            Styles.p8dding,
          w[TextView] <~ text("ambient sound ") <~
            TextTweaks.large <~ TextTweaks.italic <~ padding(top = 8 dp, bottom = 8 dp)
        )

      case 4 ⇒
        l[HorizontalLinearLayout](
          w[ImageView] <~ ImageTweaks.res(R.drawable.foursquare) <~
            Styles.p8dding,
          w[TextView] <~ wire(slots.textView) <~
            TextTweaks.large <~ padding(top = 8 dp, bottom = 8 dp)
        )

      case _ ⇒
        w[ImageView]
    }
    val card = l[CardView](l[VerticalLinearLayout](
      w[TextView] <~ Tweak[TextView](_.setTextColor(Color.GRAY)) <~ padding(all = 4 dp) <~ wire(slots.timestamp),
      view
    )) <~ Styles.card
    (card, slots)
  }

  def fillSlots(slots: Slots, data: Story.KnownElement)(implicit ctx: ActivityContext, appCtx: AppContext) = {
    val ts = Ui.nop //slots.timestamp <~ text(timestampFormat.format((chapter.start + data.timestamp) * 1000))
    val ui = data match {
      case x: Story.Image ⇒
        // format: OFF
        (slots.progress <~ show) ~
        (slots.imageView <~~ x.data.map(_.bitmapTweak(maxIconSize))) ~~
        (slots.progress <~~ fadeOut(200)) ~
        (slots.imageView <~ fadeIn(500))
      // format: ON

      case Story.TextNote(txt) ⇒
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

      case x: Story.FoursquareVenue ⇒
        slots.textView <~ text(x.name)
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
        w[ImageView] <~ ImageTweaks.adjustBounds <~ Styles.lowProfile <~ bitmapTweak

      case x: Story.TextNote ⇒
        w[TextView] <~ text(x.text) <~ TextTweaks.size(30) <~
          TextTweaks.color(Color.WHITE) <~ padding(all = 20 dp) <~
          Tweak[TextView] { v ⇒
            v.setGravity(Gravity.CENTER)
            v.setTextColor(Color.WHITE)
          }

      case x: Story.Audio ⇒
        w[TextView] <~ text("Sound")
      //        val mediaPlayer = new MediaPlayer
      //        val prepared = Promise[Unit]()
      //        x.data.foreachUi { file ⇒
      //          mediaPlayer.setDataSource(file.getAbsolutePath)
      //          mediaPlayer.prepare()
      //          mediaPlayer.setOnPreparedListener(new OnPreparedListener {
      //            override def onPrepared(mp: MediaPlayer) = prepared.success(())
      //          })
      //        }
      //        w[MediaController] <~ show <~ disable <~ prepared.future.map(_ ⇒ Tweak[MediaController] { x ⇒
      //          x.setMediaPlayer(new MediaPlayerControl {
      //            override def canPause = true
      //            override def canSeekForward = true
      //            override def canSeekBackward = true
      //            override def getAudioSessionId = mediaPlayer.getAudioSessionId
      //            override def seekTo(pos: Int) = mediaPlayer.seekTo(pos)
      //            override def getCurrentPosition = mediaPlayer.getCurrentPosition
      //            override def isPlaying = mediaPlayer.isPlaying
      //            override def pause() = mediaPlayer.pause()
      //            override def getBufferPercentage = 0
      //            override def getDuration = mediaPlayer.getDuration
      //            override def start() = mediaPlayer.start()
      //          })
      //        } + enable)

      case x: Story.FoursquareVenue ⇒
        w[TextView] <~ text("venue here")
    }
    view <~ LpTweaks.matchParent
  }
}
