package net.routestory.viewable

import android.graphics.Color
import android.view.View
import android.widget.{ TextView, ImageView }
import macroid.contrib.Layouts.HorizontalLinearLayout
import macroid.contrib.{ BgTweaks, ImageTweaks, TextTweaks }
import macroid.{ Ui, AppContext, ActivityContext }
import macroid.FullDsl._
import macroid.viewable.SlottedListable
import net.routestory.R
import net.routestory.recording.manual.ElementAdder
import net.routestory.ui.Styles

object ElementAdderListable {
  object adderListable extends SlottedListable[ElementAdder] {
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
}
