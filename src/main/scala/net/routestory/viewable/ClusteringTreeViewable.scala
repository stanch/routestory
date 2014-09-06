package net.routestory.viewable

import android.widget.{ ImageView, TextView }
import macroid.FullDsl._
import macroid.contrib.{ LpTweaks, ImageTweaks }
import macroid.viewable.Viewable
import macroid.{ ActivityContext, AppContext }
import net.routestory.data.{ Clustering, Story, Timed }
import net.routestory.ui.Tweaks
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.util.BitmapPool.Implicits._

object ClusteringTreeViewable {
  def delegate(x: Story.KnownElement)(implicit ctx: ActivityContext, appCtx: AppContext) =
    StoryElementViewable.storyElementViewable.view(x)

  def imageViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable[Story.Image] { x ⇒
    w[ImageView] <~ x.data.map(Tweaks.picasso) <~
      ImageTweaks.adjustBounds <~
      LpTweaks.matchParent
  }

  implicit def clusteringTreeViewable[A](implicit ctx: ActivityContext, appCtx: AppContext) = Viewable[Clustering.Tree[A]] {
    case Clustering.Leaf(Timed(_, x: Story.Image), _, _, _) ⇒ imageViewable.view(x)
    case Clustering.Leaf(Timed(_, x: Story.TextNote), _, _, _) ⇒ delegate(x)

    case x @ Clustering.Node(_, _, _, _) ⇒
      val elements = x.leaves.map(_.element.data)
      elements find {
        case x: Story.Image ⇒ true
        case _ ⇒ false
      } map {
        case x: Story.Image ⇒ imageViewable.view(x)
      } getOrElse {
        w[TextView]
      }

    case _ ⇒ w[TextView]
  }
}
