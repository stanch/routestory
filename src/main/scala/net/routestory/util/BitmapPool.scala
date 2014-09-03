package net.routestory.util

import java.io.File

import android.graphics.Bitmap
import android.util.LruCache
import macroid.contrib.ImageTweaks

object BitmapPool {
  implicit class RichLruCache[K, V](cache: LruCache[K, V]) {
    def getOrPut(key: K, cond: V ⇒ Boolean = _ ⇒ true)(value: ⇒ V) = cache synchronized {
      Option(cache.get(key))
        .filter(cond)
        .getOrElse {
          val newValue = value
          cache.put(key, newValue)
          newValue
        }
    }

    def getOrElse(key: K)(value: ⇒ V) =
      Option(cache.get(key)).getOrElse(value)
  }

  private val pool = new LruCache[(File, Int), Bitmap](10)

  def tweak(maxSize: Int)(file: File) = ImageTweaks.bitmap(get(maxSize)(file))

  def get(maxSize: Int)(file: File) =
    pool.getOrPut((file, maxSize), !_.isRecycled) {
      BitmapUtils.decodeFile(file, maxSize)
    }

  object Implicits {
    implicit class FileBitmap(file: File) {
      def bitmap(maxSize: Int) =
        BitmapPool.get(maxSize)(file)

      def bitmapTweak(maxSize: Int) =
        BitmapPool.tweak(maxSize)(file)
    }
  }
}
