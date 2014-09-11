package net.routestory.util

import android.support.v4.content.Loader
import macroid.ActivityContext
import resolvable.Resolvable

import scala.concurrent.ExecutionContext

class ResolvableLoader[A](query: Resolvable[A])(implicit ctx: ActivityContext, ec: ExecutionContext) extends Loader[A](ctx.get) {
  override def onStartLoading() = {
    super.onStartLoading()
    query.go.foreach(deliverResult)
  }
}
