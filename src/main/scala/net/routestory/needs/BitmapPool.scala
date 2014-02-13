package net.routestory.needs

import java.io.File
import scala.ref.WeakReference
import android.graphics.Bitmap
import net.routestory.util.BitmapUtils

object BitmapPool {
  private val _lock = new Object
  private var pool = Map.empty[(File, Int), WeakReference[Bitmap]]

  def get(maxSize: Int)(file: File) = _lock synchronized {
    pool.get(file, maxSize).flatMap(_.get).filterNot(_.isRecycled) getOrElse {
      val b = BitmapUtils.decodeFile(file, maxSize)
      pool += (file, maxSize) → WeakReference(b); b
    }
  }
}
