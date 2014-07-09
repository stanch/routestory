package net.routestory.ui

import android.content.Context
import android.util.Log
import android.view.{ View, ViewGroup }
import macroid.{ Tweak, AppContext }
import macroid.MediaQueries._

import scala.util.Random

object Spec {
  def unapply(spec: Int): Option[(Int, Int)] =
    Some(View.MeasureSpec.getSize(spec), View.MeasureSpec.getMode(spec))

  def apply(size: Int, mode: Int) = View.MeasureSpec.makeMeasureSpec(size, mode)
}

trait Grid {
  def widthToHeight: (Double, Double)
  def heightToWidth: (Double, Double)

  def inverse(r: (Double, Double)) = (1.0 / r._1, -r._2 / r._1)
  def widthFrom(height: Int) = (height * heightToWidth._1 + heightToWidth._2).toInt
  def heightFrom(width: Int) = (width * widthToHeight._1 + widthToHeight._2).toInt

  def sizeFromSpec(widthMeasureSpec: Int, heightMeasureSpec: Int) = (widthMeasureSpec, heightMeasureSpec) match {
    case (Spec(_, View.MeasureSpec.UNSPECIFIED), Spec(h, View.MeasureSpec.EXACTLY)) ⇒
      (widthFrom(h), h)
    case (Spec(w, View.MeasureSpec.EXACTLY), Spec(_, View.MeasureSpec.UNSPECIFIED)) ⇒
      (w, heightFrom(w))
    case (Spec(w, View.MeasureSpec.EXACTLY | View.MeasureSpec.AT_MOST), Spec(h, View.MeasureSpec.EXACTLY | View.MeasureSpec.AT_MOST)) ⇒
      val s = widthFrom(h)
      if (s > w) (w, heightFrom(w)) else (s, h)
    case (Spec(w, wMode), Spec(h, hMode)) ⇒
      Log.w("Grid", s"$w, $wMode, $h, $hMode"); ???
  }
}

class ProportionalCellLayout(ctx: Context) extends ViewGroup(ctx) with Grid {
  override def shouldDelayChildPressedState = false
  def child = getChildAt(0)

  var ratio = 1.0
  def widthToHeight = (ratio, 0.0)
  def heightToWidth = (1.0 / ratio, 0.0)

  override def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = {
    val (width, height) = sizeFromSpec(widthMeasureSpec, heightMeasureSpec)
    child.measure(Spec(width, View.MeasureSpec.EXACTLY), Spec(height, View.MeasureSpec.EXACTLY))
    setMeasuredDimension(width, height)
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = {
    Log.d("Grid", s"drawing only child at $left, $top, $right, $bottom")
    child.layout(0, 0, right - left, bottom - top)
  }
}

object ProportionalCellLayout {
  def ratio(r: Double) = Tweak[ProportionalCellLayout](_.ratio = r)
}

abstract class ProportionalGridLayout(ctx: Context, horizontal: Boolean) extends ViewGroup(ctx) with Grid {
  override def shouldDelayChildPressedState = false
  def children = (0 until getChildCount).map(getChildAt)
  implicit val appContext = AppContext(ctx)

  val spacing = 4.dp
  lazy val ratio: (Double, Double) = children.foldLeft((0.0, spacing.toDouble * (children.length - 1))) {
    case (r, ch: Grid) ⇒
      val r2 = if (horizontal) ch.heightToWidth else ch.widthToHeight
      (r._1 + r2._1, r._2 + r2._2)
    case _ ⇒ ???
  }
  lazy val widthToHeight = if (horizontal) inverse(ratio) else ratio
  lazy val heightToWidth = if (horizontal) ratio else inverse(ratio)

  override def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = {
    val (width, height) = sizeFromSpec(widthMeasureSpec, heightMeasureSpec)
    val (childWidth, childHeight) = if (horizontal) {
      (Spec(0, View.MeasureSpec.UNSPECIFIED), Spec(height, View.MeasureSpec.EXACTLY))
    } else {
      (Spec(width, View.MeasureSpec.EXACTLY), Spec(0, View.MeasureSpec.UNSPECIFIED))
    }
    children.foreach(_.measure(childWidth, childHeight))
    setMeasuredDimension(width, height)
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = {
    Log.d("Grid", s"onLayout $left, $top, $right, $bottom")
    children.foldLeft(0) { (s, ch) ⇒
      s + spacing + (if (horizontal) {
        ch.layout(s, 0, s + ch.getMeasuredWidth, bottom - top)
        ch.getMeasuredWidth
      } else {
        ch.layout(0, s, right - left, s + ch.getMeasuredHeight)
        ch.getMeasuredHeight
      })
    }
  }
}

class ProportionalRowLayout(ctx: Context) extends ProportionalGridLayout(ctx, true)
class ProportionalColLayout(ctx: Context) extends ProportionalGridLayout(ctx, false)
