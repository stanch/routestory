package net.routestory.viewable

import java.io.File

import android.graphics.{ BitmapFactory, Bitmap }
import android.util.LruCache
import com.google.android.gms.maps.model.Marker
import com.squareup.picasso.{ RequestCreator, Picasso }
import macroid.{ ActivityContext, AppContext }
import net.routestory.R
import net.routestory.data.{ Timed, Story, Clustering }
import net.routestory.util.BitmapUtils.MagicGrid

import scala.concurrent.{ ExecutionContext, Future }

object MarkerBitmaps {
  def picassoBitmap(maxSize: Int, rq: ⇒ RequestCreator)(implicit ec: ExecutionContext) = {
    // warm up
    rq.resize(maxSize, maxSize).centerInside().fetch()
    Future {
      scala.concurrent.blocking {
        // load
        rq.resize(maxSize, maxSize).centerInside().get()
      }
    }
  }

  def bitmapFromResource(maxSize: Int)(res: Int)(implicit appCtx: AppContext, ec: ExecutionContext) =
    picassoBitmap(maxSize, Picasso.`with`(appCtx.get).load(res))

  def bitmapFromFile(maxSize: Int)(file: File)(implicit appCtx: AppContext, ec: ExecutionContext) =
    picassoBitmap(maxSize, Picasso.`with`(appCtx.get).load(file))

  implicit class RichLruCache[K, V](cache: LruCache[K, V]) {
    def getOrPut(key: K)(value: ⇒ V) = cache synchronized {
      Option(cache.get(key)) getOrElse {
        val newValue = value
        cache.put(key, newValue)
        newValue
      }
    }
  }

  def tp[A](leaf: Clustering.Leaf[A]) = leaf.element.data match {
    case _: Story.VoiceNote ⇒ Some(R.drawable.ic_action_mic)
    case _: Story.Sound ⇒ Some(R.drawable.ic_action_volume_on)
    case _: Story.TextNote ⇒ Some(R.drawable.ic_action_view_as_list)
    case _: Story.FoursquareVenue ⇒ Some(R.drawable.foursquare)
    case _: Story.Image ⇒ None
  }

  def bitmap(maxSize: Int, cache: LruCache[Clustering.Tree[Marker], Future[Bitmap]])(tree: Clustering.Tree[Marker])(implicit ctx: ActivityContext, appCtx: AppContext, ec: ExecutionContext): Future[Bitmap] = tree match {
    case x @ Clustering.Leaf(Timed(_, _: Story.VoiceNote), _, _, _) ⇒
      bitmapFromResource(maxSize)(R.drawable.ic_action_mic)

    case x @ Clustering.Leaf(Timed(_, _: Story.Sound), _, _, _) ⇒
      bitmapFromResource(maxSize)(R.drawable.ic_action_volume_on)

    case x @ Clustering.Leaf(Timed(_, _: Story.TextNote), _, _, _) ⇒
      bitmapFromResource(maxSize)(R.drawable.ic_action_view_as_list)

    case x @ Clustering.Leaf(Timed(_, _: Story.FoursquareVenue), _, _, _) ⇒
      bitmapFromResource(maxSize)(R.drawable.foursquare)

    case x @ Clustering.Leaf(Timed(_, img: Story.Image), _, _, _) ⇒
      img.data.flatMap(bitmapFromFile(maxSize))

    case x @ Clustering.Node(_, _, _, _) ⇒
      cache.getOrPut(x) {
        val bitmaps = x.leaves.groupBy(tp).toVector.sortBy(_._1.fold(0)(_ ⇒ 1)).flatMap {
          case (None, items) ⇒
            items.map(bitmap(maxSize, cache))
          case (_, items @ Vector(i)) ⇒
            items.take(1).map(bitmap(maxSize, cache))
          case (_, items @ Vector(i, j, _*)) ⇒
            items.take(1).map(bitmap(maxSize, cache))
        }
        Future.sequence(bitmaps).map(MagicGrid.create(_, maxSize))
      }
  }
}
