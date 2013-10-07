package net.routestory.parts

import android.widget.ArrayAdapter
import android.content.Context
import android.view.{ ViewGroup, View }
import scala.util.Try
import scala.collection.JavaConversions._
import org.macroid.{ Tweaking, LayoutTransforming }

abstract class AwesomeAdapter[A, B <: View](implicit ctx: Context) extends ArrayAdapter[A](ctx, 0) {
  override def getView(position: Int, view: View, parent: ViewGroup): View = {
    val v = Option(view).flatMap(x ⇒ Try(x.asInstanceOf[B]).toOption).getOrElse(makeView)
    fillView(v, getItem(position)); v
  }
  def makeView: B
  def fillView(view: B, data: A): Any
}

object AwesomeAdapter extends Tweaking with LayoutTransforming {
  def simple[A, B <: View](data: Seq[A])(m: ⇒ B, f: A ⇒ Tweak[B])(implicit ctx: Context) = new AwesomeAdapter[A, B] {
    addAll(data)
    def makeView = m
    def fillView(view: B, data: A) = view ~> f(data)
  }
  def apply[A, B <: ViewGroup](data: Seq[A])(m: ⇒ B, f: A ⇒ Transformer)(implicit ctx: Context) = new AwesomeAdapter[A, B] {
    addAll(data)
    def makeView = m
    def fillView(view: B, data: A) = view ~~> f(data)
  }
}