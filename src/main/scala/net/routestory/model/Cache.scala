package net.routestory.model

import android.content.Context
import scala.concurrent.{ Future, ExecutionContext, future }

trait Cache[A] {
  protected def isCached(context: Context): Boolean
  protected def cache(context: Context): Boolean
  protected def retrieve(context: Context): A

  def get(implicit ctx: Context, ec: ExecutionContext) = if (isCached(ctx)) {
    Future.successful(retrieve(ctx))
  } else future {
    cache(ctx)
    retrieve(ctx)
  }

  def preload(implicit ctx: Context, ec: ExecutionContext) = if (isCached(ctx)) {
    Future.successful(true)
  } else future {
    cache(ctx)
  }
}
