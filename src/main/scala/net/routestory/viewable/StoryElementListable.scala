package net.routestory.viewable

import java.io.{ File, FileInputStream }

import android.media.MediaPlayer
import android.view.ViewGroup.LayoutParams._
import android.view.{ Gravity, View }
import android.widget._
import macroid.FullDsl._
import macroid.contrib.Layouts.{ VerticalLinearLayout, HorizontalLinearLayout }
import macroid.contrib.{ ImageTweaks, TextTweaks }
import macroid.viewable.{ Listable, SlottedListable }
import macroid._
import net.routestory.R
import net.routestory.data.Story
import net.routestory.ui.{ Styles, Tweaks }
import net.routestory.ui.SquareImageView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Promise, Future }
import scala.util.Random

object StoryElementListable {
  object imageListable extends SlottedListable[Story.Image] {
    class Slots {
      var imageView = slot[ImageView]
      var progress = slot[ProgressBar]
      var caption = slot[TextView]
    }

    val invisible = Tweak[View](_.setVisibility(View.INVISIBLE))
    val cancelAnim = Tweak[View](_.clearAnimation())

    def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val view = l[VerticalLinearLayout](
        w[TextView] <~
          wire(slots.caption) <~
          TextTweaks.medium <~ hide <~
          padding(left = 4 dp, bottom = 4 dp, right = 4 dp),
        l[FrameLayout](
          w[ProgressBar](null, android.R.attr.progressBarStyleLarge) <~
            wire(slots.progress) <~
            lp[FrameLayout](WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER),
          w[SquareImageView] <~
            wire(slots.imageView) <~
            ImageTweaks.adjustBounds
        )
      )
      (view, slots)
    }

    case class SnailingMutex[S](s: Future[S])
    implicit def `CancellableFutureTweak can snail`[W, S, R, R1](implicit canTweak: CanTweak[W, Tweak[View], R1], canSnail: CanSnail[W, Future[S], R]) =
      new CanSnail[W, SnailingMutex[S], R] {
        def snail(w: W, s: SnailingMutex[S]) = {
          lazy val promise = Promise[Nothing]()
          val tagMagic = w <~ Tweak[View] { x ⇒
            x.getTag match {
              case p: Promise[_] ⇒
                p.tryFailure(new InterruptedException)
                x.setTag(promise)
              case null ⇒
                x.setTag(promise)
              case _ ⇒
            }
          }
          val snailing = w <~~ Future.firstCompletedOf(List(s.s, promise.future))
          tagMagic ~ snailing
        }
      }

    def testIcon = Future {
      Thread.sleep(200)
      val icons = List(
        R.drawable.ic_action_camera,
        R.drawable.ic_action_mic,
        R.drawable.ic_action_place)
      icons(Random.nextInt(3))
    }

    def fillSlots(slots: Slots, data: Story.Image)(implicit ctx: ActivityContext, appCtx: AppContext) =
      (slots.caption <~ data.caption.map(text) <~ show(data.caption.isDefined)) ~
        (slots.progress <~ waitProgress(data.data)) ~
        (slots.imageView <~ data.data.map(Tweaks.picasso))
  }

  def textNoteListable(implicit ctx: ActivityContext, appCtx: AppContext) =
    Listable.text(TextTweaks.large + padding(all = 16 dp))
      .contraMap[Story.TextNote](_.text)

  object voiceNoteListable extends SlottedListable[Story.VoiceNote] {
    class Slots {
      var duration = slot[TextView]
    }

    def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val view = l[HorizontalLinearLayout](
        w[ImageView] <~ ImageTweaks.res(R.drawable.ic_action_mic) <~
          Styles.p8dding,
        w[TextView] <~ wire(slots.duration) <~
          TextTweaks.large <~ padding(top = 8 dp, bottom = 8 dp)
      )
      (view, slots)
    }

    val durationFormat = new java.text.SimpleDateFormat("HH:mm:ss") {
      setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
    }

    def fillSlots(slots: Slots, data: Story.VoiceNote)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val duration = data.data map { f ⇒
        val mp = new MediaPlayer
        val fs = new FileInputStream(f)
        mp.setDataSource(fs.getFD)
        mp.prepare()
        val d = mp.getDuration
        mp.release()
        durationFormat.format(d)
      }
      slots.duration <~ duration.map(text)
    }
  }

  def soundListable(implicit ctx: ActivityContext, appCtx: AppContext) =
    Listable[Story.Sound].tw {
      l[HorizontalLinearLayout](
        w[ImageView] <~ ImageTweaks.res(R.drawable.ic_action_volume_on) <~
          Styles.p8dding,
        w[TextView] <~ text("ambient sound ") <~
          TextTweaks.large <~ TextTweaks.italic <~ padding(top = 8 dp, bottom = 8 dp)
      )
    }(_ ⇒ Tweak.blank)

  object foursquareVenueListable extends SlottedListable[Story.FoursquareVenue] {
    class Slots {
      var name = slot[TextView]
    }

    def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val view = l[HorizontalLinearLayout](
        w[ImageView] <~ ImageTweaks.res(R.drawable.foursquare) <~
          Styles.p8dding,
        w[TextView] <~ wire(slots.name) <~
          TextTweaks.large <~ padding(top = 8 dp, bottom = 8 dp)
      )
      (view, slots)
    }

    def fillSlots(slots: Slots, data: Story.FoursquareVenue)(implicit ctx: ActivityContext, appCtx: AppContext) =
      slots.name <~ text(data.name)
  }

  implicit def storyElementListable(implicit ctx: ActivityContext, appCtx: AppContext) =
    (imageListable.toParent[Story.KnownElement] orElse
      textNoteListable.toParent[Story.KnownElement] orElse
      voiceNoteListable.toParent[Story.KnownElement] orElse
      soundListable.toParent[Story.KnownElement] orElse
      foursquareVenueListable.toParent[Story.KnownElement]).toTotal
}
