package net.routestory.viewable

import android.view.View
import android.view.ViewGroup.LayoutParams._
import android.widget.{ LinearLayout, Button }
import macroid.FullDsl._
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.viewable.{ SlottedListable, Listable }
import macroid.{ Ui, AppContext, ActivityContext }
import net.routestory.data.Story
import net.routestory.editing.ElementEditor

class ElementEditorListable(chapter: Story.Chapter) extends TimedListable(chapter) {
  object elementButtons extends SlottedListable[ElementEditor] {
    class Slots {
      var remove = slot[Button]
    }

    override def makeSlots(viewType: Int)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val view = w[LinearLayout](null, android.R.attr.buttonBarStyle) <~ addViews(List(
        w[View],
        w[LinearLayout](null, android.R.attr.buttonBarStyle) <~ addViews(List(
          w[Button](null, android.R.attr.buttonBarButtonStyle) <~
            text("Delete from story") <~ wire(slots.remove) <~
            lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT, 1.0f)
        )) <~ horizontal <~ lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT)
      )) <~ vertical <~ padding(top = 4 dp)
      (view, slots)
    }

    override def fillSlots(slots: Slots, data: ElementEditor)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      slots.remove <~ On.click(data.onRemove)
    }
  }

  def elementListable(implicit ctx: ActivityContext, appCtx: AppContext) =
    timedListable(StoryElementListable.storyElementListable)
      .contraMap[ElementEditor](_.element)
      .addFillView((view, editor) ⇒ view <~ On.click(editor.onClick))

  def elementEditorListable(implicit ctx: ActivityContext, appCtx: AppContext) =
    Listable.combine(elementListable, elementButtons) { (l1, l2) ⇒
      l[VerticalLinearLayout](l1, l2)
    }.contraMap[ElementEditor](a ⇒ (a, a))
}
