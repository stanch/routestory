package net.routestory.viewable

import android.graphics.Color
import android.view.Gravity
import android.widget.{ TextView, ImageView }
import macroid.FullDsl._
import macroid.{ AppContext, ActivityContext, Tweak }
import macroid.contrib.{ TextTweaks, LpTweaks, ImageTweaks }
import macroid.viewable.Viewable
import net.routestory.data.Story
import uk.co.senab.photoview.PhotoViewAttacher
import net.routestory.util.BitmapPool.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global

class StoryElementViewable(maxImageSize: Int) {
  def imageViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable[Story.Image, ImageView] { x ⇒
    val bitmapTweak = x.data.map(_.bitmapTweak(maxImageSize) + Tweak[ImageView] { v ⇒
      new PhotoViewAttacher(v).update()
    })
    w[ImageView] <~ ImageTweaks.adjustBounds <~ bitmapTweak <~ LpTweaks.matchParent
  }

  def textNoteViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable.text {
    TextTweaks.size(30) + TextTweaks.color(Color.WHITE) + padding(all = 20 dp) + LpTweaks.matchParent
    Tweak[TextView] { v ⇒
      v.setGravity(Gravity.CENTER)
      v.setTextColor(Color.WHITE)
    }
  }.contraMap[Story.TextNote](_.text)

  def audioViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable[Story.Audio, TextView] { x ⇒
    w[TextView] <~ text("Sound")
  }

  def foursquareVenueViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable[Story.FoursquareVenue, TextView] { x ⇒
    w[TextView] <~ text("Venue here")
  }

  implicit def storyElementViewable(implicit ctx: ActivityContext, appCtx: AppContext) =
    (imageViewable.toParent[Story.KnownElement] orElse
      textNoteViewable.toParent[Story.KnownElement] orElse
      audioViewable.toParent[Story.KnownElement] orElse
      foursquareVenueViewable.toParent[Story.KnownElement]).toTotal
}
