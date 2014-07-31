package net.routestory.viewable

import android.graphics.Color
import android.view.View
import macroid.{ ActivityContext, AppContext }
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.contrib.TextTweaks
import macroid.viewable.Listable
import macroid.FullDsl._
import net.routestory.data.{ Timed, Story }

class TimedListable(chapter: Story.Chapter) {
  val timestampFormat = java.text.DateFormat.getDateTimeInstance

  def timestampListable(implicit appCtx: AppContext) =
    Listable.text(TextTweaks.color(Color.GRAY) + padding(all = 4 dp))
      .contraMap[Int](ts ⇒ timestampFormat.format((chapter.start + ts) * 1000))

  def timedListable[A, W <: View](listable: Listable[A, W])(implicit ctx: ActivityContext, appCtx: AppContext) =
    Listable.combine(timestampListable, listable) { (ts, ls) ⇒
      l[VerticalLinearLayout](ts, ls)
    }.contraMap[Timed[A]] { t ⇒
      (t.timestamp, t.data)
    }
}
