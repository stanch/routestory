package net.routestory.viewable

import android.graphics.drawable.Drawable
import android.support.v7.widget.CardView
import android.view.View
import android.view.ViewGroup.LayoutParams._
import android.widget._
import macroid.contrib.Layouts.{ VerticalLinearLayout, HorizontalLinearLayout }
import macroid.contrib.{ BgTweaks, ImageTweaks, TextTweaks }
import macroid._
import macroid.FullDsl._
import macroid.viewable.{ Listable, SlottedListable }
import net.routestory.R
import net.routestory.data.{ Timed, Story }
import net.routestory.data.Story.KnownElement
import net.routestory.recording.ElementAdder
import net.routestory.ui.Styles
import org.apmem.tools.layouts.FlowLayout
import rx.Rx
import scala.concurrent.ExecutionContext.Implicits.global

object ElementAdderListable {
  implicit def `Rx can tweak`[W, T, R1, R2](implicit canTweak1: CanTweak[W, T, R1], canTweak2: CanTweak[W, Tweak[View], R2]) =
    new CanTweak[W, Rx[T], R2] {
      override def tweak(w: W, rx: Rx[T]) = {
        val foreach = rx.foreach(t ⇒ (w <~ t).run)
        w <~ hold(foreach)
      }
    }

  object ambientSoundListable extends SlottedListable[ElementAdder.AmbientSound] {
    class Slots {
      var switch = slot[Switch]
      var outer = slot[FlowLayout]
    }

    override def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val view = l[FlowLayout](
        w[ImageView] <~
          ImageTweaks.res(R.drawable.ic_action_volume_on),
        w[TextView] <~
          text("Ambient") <~
          TextTweaks.large <~ TextTweaks.bold <~
          padding(right = 6 sp),
        w[TextView] <~
          text("sound") <~
          TextTweaks.large <~ TextTweaks.bold <~
          padding(right = 8 dp),
        w[Switch] <~
          wire(slots.switch)
      ) <~ Styles.p8dding <~ wire(slots.outer)
      (view, slots)
    }

    def checked(checked: Boolean) = Tweak[Switch](_.setChecked(checked))

    override def fillSlots(slots: Slots, data: ElementAdder.AmbientSound)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      (slots.switch <~ data.state.map(_.map(checked))) ~
        (List(slots.switch, slots.outer) <~ On.click(data.onClick))
    }
  }

  object suggestionButtons extends SlottedListable[ElementAdder.Suggestion] {
    class Slots {
      var add = slot[Button]
      var remove = slot[Button]
    }

    def leftDrawable(drawable: Int) =
      Tweak[Button](_.setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0))

    override def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val view = w[LinearLayout](null, android.R.attr.buttonBarStyle) <~ addViews(List(
        w[View],
        w[LinearLayout](null, android.R.attr.buttonBarStyle) <~ addViews(List(
          w[Button](null, android.R.attr.buttonBarButtonStyle) <~
            text("Add to story") <~ wire(slots.add) <~
            lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT, 1.0f),
          w[Button](null, android.R.attr.buttonBarButtonStyle) <~
            text("Dismiss") <~ wire(slots.remove) <~
            lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT, 1.0f)
        )) <~ horizontal <~ lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT)
      )) <~ vertical <~ padding(top = 4 dp)
      (view, slots)
    }

    override def fillSlots(slots: Slots, data: ElementAdder.Suggestion)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      (slots.add <~ On.click(data.onAdd)) ~
        (slots.remove <~ On.click(data.onRemove))
    }
  }

  def suggestionAdderListable(implicit ctx: ActivityContext, appCtx: AppContext) =
    Listable.combine(StoryElementListable.storyElementListable, suggestionButtons) { (l1, l2) ⇒
      l[VerticalLinearLayout](l1, l2)
    }.contraMap[ElementAdder.Suggestion](a ⇒ (a.element, a))

  object mostAddersListable extends SlottedListable[ElementAdder] {
    class Slots {
      var icon = slot[ImageView]
      var caption = slot[TextView]
      var outer = slot[LinearLayout]
    }

    override def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val view = l[HorizontalLinearLayout](
        w[ImageView] <~ wire(slots.icon),
        w[TextView] <~ wire(slots.caption) <~ TextTweaks.large <~ TextTweaks.bold
      ) <~ Styles.p8dding <~ wire(slots.outer)
      (view, slots)
    }

    override def fillSlots(slots: Slots, data: ElementAdder)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val (icon, caption) = data match {
        case ElementAdder.Photo() ⇒ (R.drawable.ic_action_camera, "Photo")
        case ElementAdder.TextNote() ⇒ (R.drawable.ic_action_view_as_list, "Text note")
        case ElementAdder.VoiceNote() ⇒ (R.drawable.ic_action_mic, "Voice note")
      }
      (slots.icon <~ ImageTweaks.res(icon)) ~
        (slots.caption <~ text(caption)) ~
        (slots.outer <~ On.click(data.onClick))
    }
  }

  def adderListable(implicit ctx: ActivityContext, appCtx: AppContext) =
    (ambientSoundListable.toParent[ElementAdder] orElse
      suggestionAdderListable.toParent[ElementAdder] orElse
      mostAddersListable.toPartial).toTotal
}
