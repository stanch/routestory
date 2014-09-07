package net.routestory.viewable

import android.view.View
import android.widget._
import macroid.contrib.Layouts.HorizontalLinearLayout
import macroid.contrib.{ ImageTweaks, TextTweaks }
import macroid._
import macroid.FullDsl._
import macroid.viewable.SlottedListable
import net.routestory.R
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
          wire(slots.switch) <~
          disable
      ) <~ Styles.p8dding
      (view, slots)
    }

    def checked(checked: Boolean) = Tweak[Switch](_.setChecked(checked))

    override def fillSlots(slots: Slots, data: ElementAdder.AmbientSound)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      slots.switch <~ data.state.map(_.map(checked))
    }
  }

  object mostAddersListable extends SlottedListable[ElementAdder] {
    class Slots {
      var icon = slot[ImageView]
      var caption = slot[TextView]
    }

    override def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val view = l[HorizontalLinearLayout](
        w[ImageView] <~ wire(slots.icon),
        w[TextView] <~ wire(slots.caption) <~ TextTweaks.large <~ TextTweaks.bold
      ) <~ Styles.p8dding
      (view, slots)
    }

    override def fillSlots(slots: Slots, data: ElementAdder)(implicit ctx: ActivityContext, appCtx: AppContext) = data match {
      case ElementAdder.Photo() ⇒
        (slots.icon <~ ImageTweaks.res(R.drawable.ic_action_camera)) ~
          (slots.caption <~ text("Photo"))
      case ElementAdder.TextNote() ⇒
        (slots.icon <~ ImageTweaks.res(R.drawable.ic_action_view_as_list)) ~
          (slots.caption <~ text("Text note"))
      case ElementAdder.VoiceNote() ⇒
        (slots.icon <~ ImageTweaks.res(R.drawable.ic_action_mic)) ~
          (slots.caption <~ text("Voice note"))
    }
  }

  val adderListable =
    (ambientSoundListable.toParent[ElementAdder] orElse
      mostAddersListable.toPartial).toTotal
}
