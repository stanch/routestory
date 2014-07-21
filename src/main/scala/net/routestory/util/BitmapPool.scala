package net.routestory.util

import java.io.File

import android.graphics.Bitmap
import macroid.contrib.ImageTweaks

import scala.concurrent.ExecutionContext
import scala.ref.WeakReference

object BitmapPool {
  private val _lock = new Object
  private var pool = Map.empty[(File, Int), WeakReference[Bitmap]]

  def tweak(maxSize: Int)(file: File) = ImageTweaks.bitmap(get(maxSize)(file))

  def get(maxSize: Int)(file: File) = _lock synchronized {
    pool.get(file, maxSize).flatMap(_.get).filterNot(_.isRecycled) getOrElse {
      val b = BitmapUtils.decodeFile(file, maxSize)
      require(b != null)
      pool += (file, maxSize) → WeakReference(b); b
    }
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
