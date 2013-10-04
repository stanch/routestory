package net.routestory.parts

import android.widget.ArrayAdapter
import android.content.Context
import android.view.{ ViewGroup, View }
import scala.util.Try
import scala.collection.JavaConversions._

abstract class AwesomeAdapter[A, B <: View](implicit ctx: Context) extends ArrayAdapter[A](ctx, 0) {
  override def getView(position: Int, view: View, parent: ViewGroup): View = {
    val v = Option(view).flatMap(x ⇒ Try(x.asInstanceOf[B]).toOption).getOrElse(makeView)
    fillView(v, getItem(position)); v
  }
  def makeView: B
  def fillView(view: B, data: A): Any
}

object AwesomeAdapter {
  def apply[A, B <: View](data: A*)(m: ⇒ B)(f: B ⇒ A ⇒ Any)(implicit ctx: Context) = new AwesomeAdapter[A, B] {
    addAll(data)
    def makeView = m
    def fillView(view: B, data: A) = f(view)(data)
  }
}