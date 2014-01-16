package net.routestory.util

import java.io.{ File, FileInputStream }

import scala.util.Try

import android.graphics._
import android.graphics.Bitmap.Config
import android.graphics.Paint.Align
import android.graphics.Region.Op

object BitmapUtils {
  // see [http://stackoverflow.com/questions/477572/android-strange-out-of-memory-issue-while-loading-an-image-to-a-bitmap-object]
  def decodeFile(f: File, size: Int) = {
    val o = new BitmapFactory.Options
    o.inJustDecodeBounds = true
    BitmapFactory.decodeStream(new FileInputStream(f), null, o)
    var scale = 1
    while (Math.max(o.outWidth, o.outHeight) / scale / 2 >= size) scale *= 2

    val o2 = new BitmapFactory.Options
    o2.inSampleSize = scale
    val temp = BitmapFactory.decodeStream(new FileInputStream(f), null, o2)
    val scaled = createScaledBitmap(temp, size)
    temp.recycle()
    scaled
  }

  def createScaledBitmap(bitmap: Bitmap, size: Int) = {
    val landscape = bitmap.getWidth > bitmap.getHeight
    val width = Math.max(1, if (landscape) size else size * bitmap.getWidth / bitmap.getHeight)
    val height = Math.max(1, if (landscape) size * bitmap.getHeight / bitmap.getWidth else size)
    Bitmap.createScaledBitmap(bitmap, width, height, true)
  }

  def createScaledTransparentBitmap(bitmap: Bitmap, size: Int, alpha: Double, border: Boolean) = {
    val landscape = bitmap.getWidth > bitmap.getHeight
    val width: Int = Math.max(1, if (landscape) size else size * bitmap.getWidth / bitmap.getHeight)
    val height: Int = Math.max(1, if (landscape) size * bitmap.getHeight / bitmap.getWidth else size)
    val b = if (border) 3 else 0
    val target = Bitmap.createBitmap(width + 2 * b, height + 2 * b, Config.ARGB_8888)
    val canvas = new Canvas(target)
    canvas.save()
    canvas.clipRect(new Rect(b - 1, b - 1, width + b + 1, height + b + 1), Op.XOR)
    canvas.drawARGB(0xfa, 0xff, 0xff, 0xff)
    canvas.restore()
    val paint = new Paint
    paint.setAlpha((alpha * 255).toInt)
    canvas.drawBitmap(bitmap, null, new Rect(b, b, width + b, height + b), paint)
    target
  }

  def createCountedBitmap(bitmap: Bitmap, count: Integer) = {
    val target = Bitmap.createBitmap(bitmap.getWidth, bitmap.getHeight, Config.ARGB_8888)
    val canvas = new Canvas(target)
    val paint = new Paint
    paint.setColor(Color.WHITE)
    paint.setTextSize(bitmap.getHeight / 2)
    paint.setTextAlign(Align.RIGHT)
    canvas.drawBitmap(bitmap, 0, 0, null)
    canvas.drawText(count.toString, bitmap.getWidth, bitmap.getHeight - 3, paint)
    target
  }

  object MagicGrid {
    val spacing = 2

    def grouped[A](xs: List[A], minSize: Int) = {
      val rest = xs.length % minSize
      xs.take(rest) :: xs.drop(rest).grouped(minSize).toList
    }

    def create(bitmaps: List[Bitmap], size: Int) = {
      val root = bitmaps match {
        case a :: Nil ⇒ Cell(a)
        case a :: b :: Nil ⇒ Row(Cell(a), Cell(b))
        case a :: b :: c :: Nil ⇒ Row(Cell(a), Col(Cell(b), Cell(c)))
        case a :: b :: c :: d :: Nil ⇒ Col(Row(Cell(a), Cell(b)), Row(Cell(c), Cell(d)))
        case xs ⇒
          val d = Math.sqrt(xs.length).toInt
          Col(grouped(xs, d + 1).map(bs ⇒ Row(bs.map(Cell))))
      }
      val s = root.widthFrom(size)
      val (width, height) = if (s > size) (size, root.heightFrom(size)) else (s, size)
      def make(w: Int, h: Int): Bitmap = {
        val target = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Config.ARGB_8888)
        val canvas = new Canvas(target)
        root.draw(canvas, 0, 0, Some(Math.max(w, 1)), None) match {
          case Right(_) ⇒ target
          case Left(r) ⇒ target.recycle(); make((w / r).toInt, (h / r).toInt)
        }
      }
      make(width, height)
    }

    trait RectObject {
      val widthToHeight: (Double, Double)
      val heightToWidth: (Double, Double)
      def inverse(r: (Double, Double)) = (1.0 / r._1, -r._2 / r._1)

      def widthFrom(height: Int) = (height * heightToWidth._1 + heightToWidth._2).toInt
      def heightFrom(width: Int) = (width * widthToHeight._1 + widthToHeight._2).toInt

      def draw(canvas: Canvas, x: Int, y: Int, width: Option[Int], height: Option[Int]): Either[Double, Int]
    }

    case class Cell(bitmap: Bitmap) extends RectObject {
      lazy val widthToHeight = (bitmap.getHeight.toDouble / bitmap.getWidth, 0.0)
      lazy val heightToWidth = (bitmap.getWidth.toDouble / bitmap.getHeight, 0.0)

      def draw(canvas: Canvas, x: Int, y: Int, width: Option[Int], height: Option[Int]) = {
        val (ret, w, h) = (width, height) match {
          case (None, Some(s)) ⇒ (widthFrom(s), widthFrom(s), s)
          case (Some(s), _) ⇒ (heightFrom(s), s, heightFrom(s))
          case (None, None) ⇒ ???
        }
        val size = Math.max(w, h)
        val maxSize = Math.max(bitmap.getWidth, bitmap.getHeight)
        if (maxSize + 5 < size) {
          Left(size.toDouble / maxSize)
        } else {
          canvas.drawBitmap(bitmap, null, new Rect(x, y, x + w, y + h), null)
          Right(ret)
        }
      }
    }

    class Line(children: List[RectObject], horizontal: Boolean) extends RectObject {
      lazy val ratio = children.foldLeft((0.0, spacing.toDouble * (children.length - 1))) { (r, ch) ⇒
        val r2 = if (horizontal) ch.heightToWidth else ch.widthToHeight
        (r._1 + r2._1, r._2 + r2._2)
      }
      lazy val widthToHeight = if (horizontal) inverse(ratio) else ratio
      lazy val heightToWidth = if (horizontal) ratio else inverse(ratio)

      def draw(canvas: Canvas, x: Int, y: Int, width: Option[Int], height: Option[Int]) = {
        val (ret, w, h) = (width, height) match {
          case (None, Some(s)) ⇒ (widthFrom(s), widthFrom(s), s)
          case (Some(s), _) ⇒ (heightFrom(s), s, heightFrom(s))
          case (None, None) ⇒ ???
        }
        val z = children.foldLeft[Either[Double, Int]](Right(0)) { (acc, ch) ⇒
          acc.right.flatMap { d ⇒
            (if (horizontal) ch.draw(canvas, x + d, y, None, Some(h)) else ch.draw(canvas, x, y + d, Some(w), None)) match {
              case Right(s) ⇒ Right(d + spacing + s)
              case Left(r) ⇒ Left(r)
            }
          }
        }
        z.right.map(_ ⇒ ret)
      }
    }

    case class Row(children: List[RectObject]) extends Line(children, true)
    object Row {
      def apply(children: RectObject*): Row = apply(children.toList)
    }
    case class Col(children: List[RectObject]) extends Line(children, false)
    object Col {
      def apply(children: RectObject*): Col = apply(children.toList)
    }
  }
}
