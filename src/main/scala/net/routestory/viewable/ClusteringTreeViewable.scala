package net.routestory.viewable

import android.view.View
import android.widget.TextView
import macroid.Ui
import macroid.FullDsl._
import macroid.{ AppContext, ActivityContext }
import macroid.viewable.Viewable
import net.routestory.data.{ Timed, Story, Clustering }

class ClusteringTreeViewable[A](maxImageSize: Int) extends Viewable[Clustering.Tree[A]] {
  override type W = View

  def delegate(x: Story.KnownElement)(implicit ctx: ActivityContext, appCtx: AppContext) =
    new StoryElementDetailedViewable(maxImageSize).layout(x)

  override def layout(data: Clustering.Tree[A])(implicit ctx: ActivityContext, appCtx: AppContext) = data match {
    case Clustering.Leaf(Timed(_, x: Story.Image), _, _, _) ⇒ delegate(x)
    case Clustering.Leaf(Timed(_, x: Story.TextNote), _, _, _) ⇒ delegate(x)
    case x @ Clustering.Node(_, _, _, _) ⇒
      val elements = x.leaves.map(_.element.data)
      elements find {
        case x: Story.Image ⇒ true
        case _ ⇒ false
      } map { img ⇒
        delegate(img)
      } getOrElse {
        w[TextView]
      }
    case _ ⇒ w[TextView]
  }
}
