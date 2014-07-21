package net.routestory.util

import java.io.{ File, FileInputStream }

import android.graphics.Bitmap.Config
import android.graphics.Paint.Align
import android.graphics._
import android.support.v7.widget.CardView
import macroid.{ AppContext, ActivityContext }
import net.routestory.R
import net.routestory.ui.Styles

import macroid.Logging._
import scala.annotation.tailrec

object BitmapUtils {
  def decodeBitmapSize(f: File) = {
    val o = new BitmapFactory.Options
    o.inJustDecodeBounds = true
    BitmapFactory.decodeStream(new FileInputStream(f), null, o)
    (o.outWidth, o.outHeight)
  }

  // see [http://stackoverflow.com/questions/477572/android-strange-out-of-memory-issue-while-loading-an-image-to-a-bitmap-object]
  def decodeFile(f: File, size: Int) = {
    val (width, height) = decodeBitmapSize(f)
    var scale = 1
    while (Math.max(width, height) / scale / 2 >= size) scale *= 2

    val o2 = new BitmapFactory.Options
    o2.inSampleSize = scale
    val temp = BitmapFactory.decodeStream(new FileInputStream(f), null, o2)
    val scaled = createScaledBitmap(temp, size)
    if (scaled ne temp) temp.recycle()
    scaled
  }

  def createScaledBitmap(bitmap: Bitmap, size: Int) = {
    val landscape = bitmap.getWidth > bitmap.getHeight
    val width = Math.max(1, if (landscape) size else size * bitmap.getWidth / bitmap.getHeight)
    val height = Math.max(1, if (landscape) size * bitmap.getHeight / bitmap.getWidth else size)
    Bitmap.createScaledBitmap(bitmap, width, height, true)
  }

  def createCountedBitmap(bitmap: Bitmap, count: Integer)(implicit appCtx: AppContext) = {
    val target = Bitmap.createBitmap(bitmap.getWidth, bitmap.getHeight, Config.ARGB_8888)
    val canvas = new Canvas(target)
    val paint = new Paint
    paint.setColor(appCtx.get.getResources.getColor(R.color.aquadark))
    paint.setTextSize(bitmap.getHeight / 2)
    paint.setTextAlign(Align.RIGHT)
    canvas.drawBitmap(bitmap, 0, 0, null)
    canvas.drawText(count.toString, bitmap.getWidth, bitmap.getHeight - 3, paint)
    target
  }

  def cardFrame(bitmap: Bitmap)(implicit ctx: ActivityContext) = {
    import android.view.View.MeasureSpec._
    val card = new CardView(ctx.get)
    Styles.card.apply(card)
    val (width, height) = (bitmap.getWidth + 21, bitmap.getHeight + 21)
    card.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY))
    card.layout(0, 0, width, height)
    card.buildDrawingCache()
    val frame = Bitmap.createBitmap(width, height, Config.ARGB_8888)
    val canvas = new Canvas(frame)
    card.draw(canvas)
    canvas.drawBitmap(bitmap, 10f, 10f, null)
    frame
  }

  object MagicGrid {
    val spacing = 2

    def grouped[A](xs: Vector[A], minSize: Int) = {
      val rest = xs.length % minSize
      if (rest > 0) {
        xs.take(rest) +: xs.drop(rest).grouped(minSize).toVector
      } else {
        xs.grouped(minSize).toVector
      }
    }

    def create(bitmaps: Vector[Bitmap], size: Int) = {
      val root = bitmaps match {
        case Vector(a) ⇒ Cell(a)
        case Vector(a, b) ⇒ Row(Cell(a), Cell(b))
        case Vector(a, b, c) ⇒ Row(Cell(a), Col(Cell(b), Cell(c)))
        case Vector(a, b, c, d) ⇒ Col(Row(Cell(a), Cell(b)), Row(Cell(c), Cell(d)))
        case xs ⇒
          val d = Math.sqrt(xs.length).toInt
          Col(grouped(xs, d + 1).map(bs ⇒ Row(bs.map(Cell))))
      }
      val s = root.widthFrom(size)
      val (width, height) = if (s > size) (size, root.heightFrom(size)) else (s, size)
      @tailrec def make(w: Int, h: Int): Bitmap = {
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

      def resolveSize(width: Option[Int], height: Option[Int]) = (width, height) match {
        case (None, Some(s)) ⇒ (widthFrom(s), widthFrom(s), s)
        case (Some(s), _) ⇒ (heightFrom(s), s, heightFrom(s))
        case (None, None) ⇒ ???
      }

      def draw(canvas: Canvas, x: Int, y: Int, width: Option[Int], height: Option[Int]): Either[Double, Int]
    }

    case class Cell(bitmap: Bitmap) extends RectObject {
      lazy val widthToHeight = (bitmap.getHeight.toDouble / bitmap.getWidth, 0.0)
      lazy val heightToWidth = (bitmap.getWidth.toDouble / bitmap.getHeight, 0.0)

      def draw(canvas: Canvas, x: Int, y: Int, width: Option[Int], height: Option[Int]) = {
        val (ret, w, h) = resolveSize(width, height)
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

    class Line(children: Vector[RectObject], horizontal: Boolean) extends RectObject {
      lazy val ratio = children.foldLeft((0.0, spacing.toDouble * (children.length - 1))) { (r, ch) ⇒
        val r2 = if (horizontal) ch.heightToWidth else ch.widthToHeight
        (r._1 + r2._1, r._2 + r2._2)
      }
      lazy val widthToHeight = if (horizontal) inverse(ratio) else ratio
      lazy val heightToWidth = if (horizontal) ratio else inverse(ratio)

      def draw(canvas: Canvas, x: Int, y: Int, width: Option[Int], height: Option[Int]) = {
        val (ret, w, h) = resolveSize(width, height)
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

    case class Row(children: Vector[RectObject]) extends Line(children, true)
    object Row {
      def apply(children: RectObject*): Row = apply(children.toVector)
    }
    case class Col(children: Vector[RectObject]) extends Line(children, false)
    object Col {
      def apply(children: RectObject*): Col = apply(children.toVector)
    }
  }
}
