package net.routestory.viewable

import android.graphics.{ BitmapFactory, Bitmap }
import macroid.{ ActivityContext, AppContext }
import net.routestory.R
import net.routestory.data.{ Story, Clustering }
import net.routestory.util.BitmapPool.Implicits._
import net.routestory.util.BitmapUtils
import net.routestory.util.BitmapUtils.MagicGrid

import scala.concurrent.{ ExecutionContext, Future }

object MarkerBitmaps {
  def stock(res: Int)(implicit appCtx: AppContext) = Future.successful {
    BitmapFactory.decodeResource(appCtx.get.getResources, res)
  }

  def tp[A](leaf: Clustering.Leaf[A]) = leaf.element match {
    case _: Story.VoiceNote ⇒ Some(R.drawable.ic_action_mic)
    case _: Story.Sound ⇒ Some(R.drawable.ic_action_volume_on)
    case _: Story.TextNote ⇒ Some(R.drawable.ic_action_view_as_list)
    case _: Story.Venue ⇒ Some(R.drawable.foursquare)
    case _: Story.Image ⇒ None
  }

  def withBitmaps(maxSize: Int)(tree: Clustering.Tree[Unit])(implicit ctx: ActivityContext, appCtx: AppContext, ec: ExecutionContext): Clustering.Tree[Future[Bitmap]] = tree match {
    case x @ Clustering.Leaf(_: Story.VoiceNote, _, _) ⇒ x.withData(stock(R.drawable.ic_action_mic))
    case x @ Clustering.Leaf(_: Story.Sound, _, _) ⇒ x.withData(stock(R.drawable.ic_action_volume_on))
    case x @ Clustering.Leaf(_: Story.TextNote, _, _) ⇒ x.withData(stock(R.drawable.ic_action_view_as_list))
    case x @ Clustering.Leaf(_: Story.Venue, _, _) ⇒ x.withData(stock(R.drawable.foursquare))

    case x @ Clustering.Leaf(img: Story.Image, _, _) ⇒
      x.withData(img.data.map(_.bitmap(maxSize)))

    case x @ Clustering.Node(_, _, _) ⇒
      val children = x.children.map(withBitmaps(maxSize))
      val intermediate = x.withChildren(children, Future.failed(new Exception))
      val bitmaps = intermediate.leaves.groupBy(tp).toVector.sortBy(_._1.fold(0)(_ ⇒ 1)).flatMap {
        case (None, items) ⇒
          items.map(_.data)
        case (_, items @ Vector(i)) ⇒
          items.take(1).map(_.data)
        case (_, items @ Vector(i, j, _*)) ⇒
          items.take(1).map(_.data)
        //items.take(1).map(_.data.map(BitmapUtils.createCountedBitmap(_, items.length)))
      }
      val grid = Future.sequence(bitmaps).map(MagicGrid.create(_, maxSize))
      intermediate.withData(grid)
  }
}
