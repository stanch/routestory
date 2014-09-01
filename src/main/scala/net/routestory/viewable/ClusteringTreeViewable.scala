package net.routestory.viewable

import android.widget.{ ImageView, TextView }
import macroid.FullDsl._
import macroid.contrib.{ LpTweaks, ImageTweaks }
import macroid.viewable.Viewable
import macroid.{ ActivityContext, AppContext }
import net.routestory.data.{ Clustering, Story, Timed }
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.util.BitmapPool.Implicits._

class ClusteringTreeViewable[A](maxImageSize: Int) {
  def delegate(x: Story.KnownElement)(implicit ctx: ActivityContext, appCtx: AppContext) =
    new StoryElementViewable(maxImageSize).storyElementViewable.view(x)

  def imageViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable[Story.Image] { x ⇒
    val bitmapTweak = x.data.map(_.bitmapTweak(maxImageSize))
    w[ImageView] <~ bitmapTweak <~
      ImageTweaks.adjustBounds <~
      LpTweaks.matchParent
  }

  implicit def clusteringTreeViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable[Clustering.Tree[A]] {
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
