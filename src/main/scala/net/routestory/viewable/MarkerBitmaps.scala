package net.routestory.viewable

import android.graphics.{ BitmapFactory, Bitmap }
import macroid.{ ActivityContext, AppContext }
import net.routestory.R
import net.routestory.data.{ Timed, Story, Clustering }
import net.routestory.util.BitmapPool.Implicits._
import net.routestory.util.BitmapUtils
import net.routestory.util.BitmapUtils.MagicGrid

import scala.concurrent.{ ExecutionContext, Future }

object MarkerBitmaps {
  def stock(res: Int)(implicit appCtx: AppContext) = Future.successful {
    BitmapFactory.decodeResource(appCtx.get.getResources, res)
  }

  def tp[A](leaf: Clustering.Leaf[A]) = leaf.element.data match {
    case _: Story.VoiceNote ⇒ Some(R.drawable.ic_action_mic)
    case _: Story.Sound ⇒ Some(R.drawable.ic_action_volume_on)
    case _: Story.TextNote ⇒ Some(R.drawable.ic_action_view_as_list)
    case _: Story.FoursquareVenue ⇒ Some(R.drawable.foursquare)
    case _: Story.Image ⇒ None
  }

  type BitmapCache = Map[Clustering.Tree[Unit], Future[Bitmap]]

  def withBitmaps(maxSize: Int, cached: BitmapCache)(tree: Clustering.Tree[Unit])(implicit ctx: ActivityContext, appCtx: AppContext, ec: ExecutionContext): (Clustering.Tree[Future[Bitmap]], BitmapCache) = tree match {
    case x @ Clustering.Leaf(Timed(_, _: Story.VoiceNote), _, _, _) ⇒
      x.withData(stock(R.drawable.ic_action_mic)) → Map.empty
    case x @ Clustering.Leaf(Timed(_, _: Story.Sound), _, _, _) ⇒
      x.withData(stock(R.drawable.ic_action_volume_on)) → Map.empty
    case x @ Clustering.Leaf(Timed(_, _: Story.TextNote), _, _, _) ⇒
      x.withData(stock(R.drawable.ic_action_view_as_list)) → Map.empty
    case x @ Clustering.Leaf(Timed(_, _: Story.FoursquareVenue), _, _, _) ⇒
      x.withData(stock(R.drawable.foursquare)) → Map.empty

    case x @ Clustering.Leaf(Timed(_, img: Story.Image), _, _, _) ⇒
      val bitmap = cached.getOrElse(x, img.data.map(_.bitmap(maxSize)))
      x.withData(bitmap) → Map(x → bitmap)

    case x @ Clustering.Node(_, _, _, _) ⇒
      val (children, caches) = x.children.map(withBitmaps(maxSize, cached)).unzip
      val intermediate = x.withChildren(children, Future.failed(new Exception))
      val bitmap = cached.getOrElse(x, {
        val bitmaps = intermediate.leaves.groupBy(tp).toVector.sortBy(_._1.fold(0)(_ ⇒ 1)).flatMap {
          case (None, items) ⇒
            items.map(_.data)
          case (_, items @ Vector(i)) ⇒
            items.take(1).map(_.data)
          case (_, items @ Vector(i, j, _*)) ⇒
            items.take(1).map(_.data)
          //items.take(1).map(_.data.map(BitmapUtils.createCountedBitmap(_, items.length)))
        }
        Future.sequence(bitmaps).map(MagicGrid.create(_, maxSize))
      })

      intermediate.withData(bitmap) → (caches.reduce(_ ++ _) ++ Map(x → bitmap))
  }
}
