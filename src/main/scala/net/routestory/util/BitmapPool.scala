package net.routestory.util

import java.io.File
import java.util.NoSuchElementException
import java.util.concurrent.Executors

import android.graphics.Bitmap
import android.util.LruCache
import macroid.contrib.ImageTweaks

import scala.concurrent.{ ExecutionContext, Future }

object BitmapPool {
  lazy val processingEc = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

  implicit class RichLruCache[K, V](cache: LruCache[K, Future[V]]) {
    def getOrPut(key: K, cond: V ⇒ Boolean = _ ⇒ true)(value: ⇒ Future[V])(implicit ec: ExecutionContext) = cache synchronized {
      Option(cache.get(key))
        .map { f ⇒
          f.filter(cond).recoverWith {
            case _: NoSuchElementException ⇒
              val newValue = value
              cache.synchronized(cache.put(key, newValue))
              newValue
          }
        }
        .getOrElse {
          val newValue = value
          cache.put(key, newValue)
          newValue
        }
    }

    def getOrElse(key: K)(value: ⇒ Future[V]) =
      Option(cache.get(key)).getOrElse(value)
  }

  private val pool = new LruCache[(File, Int), Future[Bitmap]](10)

  def tweak(maxSize: Int)(file: File)(implicit ctx: ExecutionContext) =
    get(maxSize)(file).map(ImageTweaks.bitmap)

  def get(maxSize: Int)(file: File)(implicit ctx: ExecutionContext): Future[Bitmap] = {
    pool.getOrPut((file, maxSize))(Future {
      scala.concurrent.blocking(BitmapUtils.decodeFile(file, maxSize))
    }(processingEc))
  }

  object Implicits {
    implicit class FileBitmap(file: File) {
      def bitmap(maxSize: Int)(implicit ec: ExecutionContext) =
        BitmapPool.get(maxSize)(file)

      def bitmapTweak(maxSize: Int)(implicit ec: ExecutionContext) =
        BitmapPool.tweak(maxSize)(file)
    }
  }
}
