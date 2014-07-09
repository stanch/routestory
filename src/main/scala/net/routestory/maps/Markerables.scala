package net.routestory.maps

import android.content.Intent
import android.graphics.{ Bitmap, BitmapFactory }
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup.LayoutParams._
import android.widget.{ Button, FrameLayout, ImageView, TextView }
import com.google.android.gms.maps.model.LatLng
import macroid.FullDsl._
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.util.Ui
import macroid.{ ActivityContext, AppContext, Tweak }
import net.routestory.R
import net.routestory.data.Story
import net.routestory.ui.Styles._
import net.routestory.util.BitmapPool.Implicits._
import net.routestory.util.Implicits._
import uk.co.senab.photoview.PhotoViewAttacher

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

trait Markerable[A] {
  /** Handle marker click */
  def click(data: A)(implicit ctx: ActivityContext, appCtx: AppContext): Ui[Any]

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
    def click(data: Story.Image)(implicit ctx: ActivityContext, appCtx: AppContext) = Ui {
      data.data.map(_.bitmapTweak(displaySize.max)) foreachUi { bitmapTweak ⇒
        val view = w[ImageView] <~ bitmapTweak <~ Image.adjustBounds <~ lowProfile
        new PhotoViewAttacher(getUi(view)).update()
        // TODO: recycle the bitmap on dismiss?
        getUi {
          dialog(android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
            l[FrameLayout](
              view <~ lp[FrameLayout](MATCH_PARENT, MATCH_PARENT, Gravity.CENTER)
            )
          } <~ speak
        }
      }
    }
    def icon(data: Story.Image, maxSize: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = data.data.map(_.bitmap(maxSize))
    def iconType(data: Story.Image) = None
  }

  implicit object AudioMarkerable extends TimedMarkerable[Story.Audio] {
    private var mediaPlayer: Option[MediaPlayer] = None
    def click(data: Story.Audio)(implicit ctx: ActivityContext, appCtx: AppContext) = Ui {
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
        w[TextView] <~ text(data.text) <~ TextSize.medium <~ p8dding <~
          Tweak[TextView](_.setMaxWidth((displaySize(0) * 0.75).toInt))
      } <~ speak
    }
    val iconResource = R.drawable.text_note
  }

  implicit object VenueMarkerable extends TimedMarkerable[Story.Venue] with StockIconMarkerable[Story.Venue] {
    def click(data: Story.Venue)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      dialog {
        l[VerticalLinearLayout](
          w[TextView] <~ text(data.name) <~ TextSize.large <~ padding(left = 3 sp),
          w[Button] <~ text("Open in Foursquare®") <~ On.click(Ui {
            val intent = new Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse(s"https://foursquare.com/v/${data.id}"))
            ctx.get.startActivityForResult(intent, 0)
          })
        )
      } <~ speak
    }
    val iconResource = R.drawable.foursquare
  }

  implicit object MediaMarkerable extends TimedMarkerable[Story.KnownElement] {
    def click(data: Story.KnownElement)(implicit ctx: ActivityContext, appCtx: AppContext) = data match {
      case m: Story.Image ⇒ implicitly[Markerable[Story.Image]].click(m)
      case m: Story.Audio ⇒ implicitly[Markerable[Story.Audio]].click(m)
      case m: Story.TextNote ⇒ implicitly[Markerable[Story.TextNote]].click(m)
      case m: Story.Venue ⇒ implicitly[Markerable[Story.Venue]].click(m)
    }
    def icon(data: Story.KnownElement, maxSize: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = data match {
      case m: Story.Image ⇒ implicitly[Markerable[Story.Image]].icon(m, maxSize)
      case m: Story.Audio ⇒ implicitly[Markerable[Story.Audio]].icon(m, maxSize)
      case m: Story.TextNote ⇒ implicitly[Markerable[Story.TextNote]].icon(m, maxSize)
      case m: Story.Venue ⇒ implicitly[Markerable[Story.Venue]].icon(m, maxSize)
    }
    def iconType(data: Story.KnownElement) = data match {
      case m: Story.Image ⇒ implicitly[Markerable[Story.Image]].iconType(m)
      case m: Story.Audio ⇒ implicitly[Markerable[Story.Audio]].iconType(m)
      case m: Story.TextNote ⇒ implicitly[Markerable[Story.TextNote]].iconType(m)
      case m: Story.Venue ⇒ implicitly[Markerable[Story.Venue]].iconType(m)
    }
  }
}
