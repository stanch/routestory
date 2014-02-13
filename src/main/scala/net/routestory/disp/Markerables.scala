package net.routestory.disp

import org.macroid.{ Tweak, AppContext, ActivityContext }
import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import net.routestory.ui.Styles._

import net.routestory.model.Story
import android.widget.{ Button, TextView, FrameLayout, ImageView }
import uk.co.senab.photoview.PhotoViewAttacher
import android.view.Gravity
import android.content.{ Intent, Context }
import android.os.Vibrator
import android.net.Uri
import android.view.ViewGroup.LayoutParams._
import android.media.MediaPlayer
import scala.util.Try
import scala.concurrent.Future
import android.graphics.{ BitmapFactory, Bitmap }
import net.routestory.R
import com.google.android.gms.maps.model.LatLng
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.model.MediaOps._

trait Markerable[A] {
  /** Handle marker click */
  def click(data: A)(implicit ctx: ActivityContext, appCtx: AppContext): Unit

  /** Fetch marker icon */
  def icon(data: A, maxSize: Int)(implicit ctx: ActivityContext, appCtx: AppContext): Future[Bitmap]

  /** Icon type for grouping */
  def iconType(data: A): Option[Int]

  /** Coordinates */
  def location(data: A): LatLng
}

class Markerables(displaySize: List[Int], chapter: Story.Chapter) {
  trait TimedMarkerable[A <: Story.Timed] extends Markerable[A] {
    def location(data: A) = chapter.locationAt(data.timestamp)
  }

  trait StockIconMarkerable[A] extends Markerable[A] {
    val iconResource: Int
    def icon(data: A, maxSize: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = Future.successful {
      BitmapFactory.decodeResource(ctx.get.getResources, iconResource)
    }
    def iconType(data: A) = Some(iconResource)
  }

  implicit object ImageMarkerable extends TimedMarkerable[Story.Image] {
    def click(data: Story.Image)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      data.bitmap(displaySize.max) foreachUi { bitmap ⇒
        val view = w[ImageView] ~> Image.bitmap(bitmap) ~> Image.adjustBounds ~> lowProfile
        new PhotoViewAttacher(view).update()
        // TODO: recycle the bitmap on dismiss?
        dialog(android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
          l[FrameLayout](
            view ~> lp(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER)
          )
        } ~> speak
      }
    }
    def icon(data: Story.Image, maxSize: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = data.bitmap(maxSize)
    def iconType(data: Story.Image) = None
  }

  implicit object AudioMarkerable extends TimedMarkerable[Story.Audio] {
    private var mediaPlayer: Option[MediaPlayer] = None
    def click(data: Story.Audio)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      mediaPlayer = Some(new MediaPlayer)
      data.data foreachUi { file ⇒
        Try {
          mediaPlayer.foreach { p ⇒
            p.setDataSource(file.getAbsolutePath)
            p.prepare()
            p.start()
          }
        }
      }
    }
    def icon(data: Story.Audio, maxSize: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = Future.successful {
      BitmapFactory.decodeResource(appCtx.get.getResources, data match {
        case m: Story.Sound ⇒ R.drawable.sound
        case m: Story.VoiceNote ⇒ R.drawable.voice_note
      })
    }
    def iconType(data: Story.Audio) = data match {
      case m: Story.Sound ⇒ Some(R.drawable.sound)
      case m: Story.VoiceNote ⇒ Some(R.drawable.voice_note)
    }
  }

  implicit object TextNoteMarkerable extends TimedMarkerable[Story.TextNote] with StockIconMarkerable[Story.TextNote] {
    def click(data: Story.TextNote)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      dialog {
        w[TextView] ~> text(data.text) ~> TextSize.medium ~> p8dding ~>
          Tweak[TextView](_.setMaxWidth((displaySize(0) * 0.75).toInt))
      } ~> speak
    }
    val iconResource = R.drawable.text_note
  }

  implicit object HeartbeatMarkerable extends TimedMarkerable[Story.Heartbeat] with StockIconMarkerable[Story.Heartbeat] {
    def click(data: Story.Heartbeat)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      ctx.get.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator].vibrate(data.vibrationPattern(5), -1)
    }
    val iconResource = R.drawable.heart
  }

  implicit object VenueMarkerable extends TimedMarkerable[Story.Venue] with StockIconMarkerable[Story.Venue] {
    def click(data: Story.Venue)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      dialog {
        l[VerticalLinearLayout](
          w[TextView] ~> text(data.name) ~> TextSize.large ~> padding(left = 3 sp),
          w[Button] ~> text("Open in Foursquare®") ~> On.click {
            val intent = new Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse(s"https://foursquare.com/v/${data.id}"))
            ctx.get.startActivityForResult(intent, 0)
          }
        )
      } ~> speak
    }
    val iconResource = R.drawable.foursquare
  }

  implicit object MediaMarkerable extends TimedMarkerable[Story.KnownMedia] {
    def click(data: Story.KnownMedia)(implicit ctx: ActivityContext, appCtx: AppContext) = data match {
      case m: Story.Image ⇒ implicitly[Markerable[Story.Image]].click(m)
      case m: Story.Audio ⇒ implicitly[Markerable[Story.Audio]].click(m)
      case m: Story.TextNote ⇒ implicitly[Markerable[Story.TextNote]].click(m)
      case m: Story.Heartbeat ⇒ implicitly[Markerable[Story.Heartbeat]].click(m)
      case m: Story.Venue ⇒ implicitly[Markerable[Story.Venue]].click(m)
    }
    def icon(data: Story.KnownMedia, maxSize: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = data match {
      case m: Story.Image ⇒ implicitly[Markerable[Story.Image]].icon(m, maxSize)
      case m: Story.Audio ⇒ implicitly[Markerable[Story.Audio]].icon(m, maxSize)
      case m: Story.TextNote ⇒ implicitly[Markerable[Story.TextNote]].icon(m, maxSize)
      case m: Story.Heartbeat ⇒ implicitly[Markerable[Story.Heartbeat]].icon(m, maxSize)
      case m: Story.Venue ⇒ implicitly[Markerable[Story.Venue]].icon(m, maxSize)
    }
    def iconType(data: Story.KnownMedia) = data match {
      case m: Story.Image ⇒ implicitly[Markerable[Story.Image]].iconType(m)
      case m: Story.Audio ⇒ implicitly[Markerable[Story.Audio]].iconType(m)
      case m: Story.TextNote ⇒ implicitly[Markerable[Story.TextNote]].iconType(m)
      case m: Story.Heartbeat ⇒ implicitly[Markerable[Story.Heartbeat]].iconType(m)
      case m: Story.Venue ⇒ implicitly[Markerable[Story.Venue]].iconType(m)
    }
  }
}
