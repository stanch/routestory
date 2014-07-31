package net.routestory.viewable

import android.view.View
import android.widget.TextView
import macroid.FullDsl._
import macroid.viewable.Viewable
import macroid.{ ActivityContext, AppContext }
import net.routestory.data.{ Clustering, Story, Timed }

class ClusteringTreeViewable[A](maxImageSize: Int) {
  def delegate(x: Story.KnownElement)(implicit ctx: ActivityContext, appCtx: AppContext) =
    new StoryElementViewable(maxImageSize).storyElementViewable.layout(x)

  implicit def clusteringTreeViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable[Clustering.Tree[A]] {
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
