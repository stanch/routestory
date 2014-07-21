package net.routestory.viewable

import android.view.View
import android.widget.TextView
import macroid.Ui
import macroid.FullDsl._
import macroid.{ AppContext, ActivityContext }
import macroid.viewable.Viewable
import net.routestory.data.{ Story, Clustering }

class ClusteringTreeViewable[A](maxImageSize: Int) extends Viewable[Clustering.Tree[A]] {
  override type W = View

  def delegate(x: Story.KnownElement)(implicit ctx: ActivityContext, appCtx: AppContext) =
    new StoryElementDetailedViewable(maxImageSize).layout(x)

  override def layout(data: Clustering.Tree[A])(implicit ctx: ActivityContext, appCtx: AppContext) = data match {
    case Clustering.Leaf(x: Story.Image, _, _) ⇒ delegate(x)
    case Clustering.Leaf(x: Story.TextNote, _, _) ⇒ delegate(x)
    case x @ Clustering.Node(_, _, _) ⇒
      val elements = x.leaves.map(_.element)
      if (elements.exists(_.isInstanceOf[Story.Image])) {
        delegate(elements.find(_.isInstanceOf[Story.Image]).get)
      } else {
        w[TextView]
      }
    case _ ⇒ w[TextView]
  }
}
