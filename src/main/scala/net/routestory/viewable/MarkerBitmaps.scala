package net.routestory.viewable

import android.graphics.{ BitmapFactory, Bitmap }
import android.util.LruCache
import com.google.android.gms.maps.model.Marker
import macroid.{ ActivityContext, AppContext }
import net.routestory.R
import net.routestory.data.{ Timed, Story, Clustering }
import net.routestory.util.BitmapPool.Implicits._
import net.routestory.util.BitmapPool._
import net.routestory.util.BitmapUtils.MagicGrid

import scala.concurrent.{ ExecutionContext, Future }

object MarkerBitmaps {
  val stockCache = new LruCache[Int, Bitmap](4)

  def stock(res: Int)(implicit appCtx: AppContext) = Future.successful {
    stockCache.getOrPut(res, !_.isRecycled) {
      BitmapFactory.decodeResource(appCtx.get.getResources, res)
    }
  }

  def tp[A](leaf: Clustering.Leaf[A]) = leaf.element.data match {
    case _: Story.VoiceNote ⇒ Some(R.drawable.ic_action_mic)
    case _: Story.Sound ⇒ Some(R.drawable.ic_action_volume_on)
    case _: Story.TextNote ⇒ Some(R.drawable.ic_action_view_as_list)
    case _: Story.FoursquareVenue ⇒ Some(R.drawable.foursquare)
    case _: Story.Image ⇒ None
  }

  val bitmapCache = new LruCache[Clustering.Tree[Marker], Future[Bitmap]](10)

  def bitmap(maxSize: Int)(tree: Clustering.Tree[Marker])(implicit ctx: ActivityContext, appCtx: AppContext, ec: ExecutionContext): Future[Bitmap] = tree match {
    case x @ Clustering.Leaf(Timed(_, _: Story.VoiceNote), _, _, _) ⇒ stock(R.drawable.ic_action_mic)
    case x @ Clustering.Leaf(Timed(_, _: Story.Sound), _, _, _) ⇒ stock(R.drawable.ic_action_volume_on)
    case x @ Clustering.Leaf(Timed(_, _: Story.TextNote), _, _, _) ⇒ stock(R.drawable.ic_action_view_as_list)
    case x @ Clustering.Leaf(Timed(_, _: Story.FoursquareVenue), _, _, _) ⇒ stock(R.drawable.foursquare)

    case x @ Clustering.Leaf(Timed(_, img: Story.Image), _, _, _) ⇒
      bitmapCache.getOrPut(x)(img.data.map(_.bitmap(maxSize)))

    case x @ Clustering.Node(_, _, _, _) ⇒
      // no sense in caching composite bitmaps
      bitmapCache.getOrElse(x) {
        val bitmaps = x.leaves.groupBy(tp).toVector.sortBy(_._1.fold(0)(_ ⇒ 1)).flatMap {
          case (None, items) ⇒
            items.map(bitmap(maxSize))
          case (_, items @ Vector(i)) ⇒
            items.take(1).map(bitmap(maxSize))
          case (_, items @ Vector(i, j, _*)) ⇒
            items.take(1).map(bitmap(maxSize))
        }
        Future.sequence(bitmaps).map(MagicGrid.create(_, maxSize))
      }
  }
}
