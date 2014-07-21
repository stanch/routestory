package net.routestory.viewable

import akka.actor.ActorSelection
import android.view.ViewGroup.LayoutParams._
import android.widget.{ ImageView, LinearLayout, TextView }
import macroid.FullDsl._
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.contrib.{ ImageTweaks, TextTweaks }
import macroid.Ui
import macroid.viewable.SlottedFillableViewable
import macroid.{ ActivityContext, AppContext, Tweak }
import net.routestory.ui.Styles._
import net.routestory.{ Apis, R }
import org.needs.{ flickr, foursquare }

case class Suggestables(apis: Apis, typewriter: ActorSelection) {
  implicit object foursquareListViewable extends SlottedFillableViewable[foursquare.Venue] {
    class Slots {
      var name = slot[TextView]
      var row = slot[LinearLayout]
    }
    def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val layout = l[HorizontalLinearLayout](
        w[ImageView] <~ ImageTweaks.res(R.drawable.foursquare),
        w[TextView] <~ wire(slots.name) <~ TextTweaks.medium
      ) <~ wire(slots.row)
      (layout, slots)
    }
    def fillSlots(slots: Slots, data: foursquare.Venue)(implicit ctx: ActivityContext, appCtx: AppContext) = Ui.sequence(
      slots.name <~ text(data.name),
      slots.row <~ On.click(Ui(typewriter ! data))
    )
  }

  implicit object flickrListViewable extends SlottedFillableViewable[flickr.Photo] {
    class Slots {
      var title = slot[TextView]
      var owner = slot[TextView]
      var pic = slot[ImageView]
      var row = slot[LinearLayout]
    }
    def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val layout = l[VerticalLinearLayout](
        w[TextView] <~ wire(slots.title),
        w[TextView] <~ wire(slots.owner),
        w[ImageView] <~ wire(slots.pic) <~ lp[LinearLayout](100 dp, WRAP_CONTENT)
      ) <~ wire(slots.row) <~ p8dding
      (layout, slots)
    }
    def fillSlots(slots: Slots, data: flickr.Photo)(implicit ctx: ActivityContext, appCtx: AppContext) = Ui.sequence(
      slots.title <~ text(data.title),
      slots.owner <~ text(s"by ${data.owner.name}"),
      //slots.pic <~ apis.api.NeedBitmap(data.url, 100 dp).go.map(b ⇒ Image.bitmap(b) + Image.adjustBounds)
      slots.row <~ On.click(Ui(typewriter ! data))
    )
  }
}
