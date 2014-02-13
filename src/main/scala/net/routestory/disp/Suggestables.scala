package net.routestory.disp

import net.routestory.{ R, Apis }
import akka.actor.ActorSelection
import org.macroid.viewable.SlottedFillableViewable
import org.needs.{ flickr, foursquare }
import org.macroid.FullDsl._
import android.widget.{ ImageView, LinearLayout, TextView }
import org.macroid.contrib.Layouts.{ VerticalLinearLayout, HorizontalLinearLayout }
import org.macroid.contrib.ExtraTweaks.{ Image, TextSize }
import android.view.ViewGroup.LayoutParams._
import org.macroid.AppContext
import org.macroid.ActivityContext
import net.routestory.ui.Styles._
import scala.concurrent.ExecutionContext.Implicits.global

case class Suggestables(apis: Apis, typewriter: ActorSelection) {
  implicit object foursquareListViewable extends SlottedFillableViewable[foursquare.Venue] {
    class Slots {
      var name = slot[TextView]
      var row = slot[LinearLayout]
    }
    def makeSlots(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val layout = l[HorizontalLinearLayout](
        w[ImageView] ~> org.macroid.Tweak[ImageView](_.setImageResource(R.drawable.foursquare)),
        w[TextView] ~> wire(slots.name) ~> TextSize.medium
      ) ~> wire(slots.row)
      (layout, slots)
    }
    def fillSlots(slots: Slots, data: foursquare.Venue)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      slots.name ~> text(data.name)
      slots.row ~> On.click(typewriter ! data)
    }
  }

  implicit object flickrListViewable extends SlottedFillableViewable[flickr.Photo] {
    class Slots {
      var title = slot[TextView]
      var owner = slot[TextView]
      var pic = slot[ImageView]
      var row = slot[LinearLayout]
    }
    def makeSlots(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val layout = l[VerticalLinearLayout](
        w[TextView] ~> wire(slots.title),
        w[TextView] ~> wire(slots.owner),
        w[ImageView] ~> wire(slots.pic) ~> lp(100 dp, WRAP_CONTENT)
      ) ~> wire(slots.row) ~> p8dding
      (layout, slots)
    }
    def fillSlots(slots: Slots, data: flickr.Photo)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      slots.title ~> text(data.title)
      slots.owner ~> text(s"by ${data.owner.name}")
      //slots.pic ~> apis.api.NeedBitmap(data.url, 100 dp).go.map(b ⇒ Image.bitmap(b) + Image.adjustBounds)
      slots.row ~> On.click(typewriter ! data)
    }
  }
}
