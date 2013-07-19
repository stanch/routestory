package net.routestory.model

import android.content.Context
import scala.concurrent.{ Future, ExecutionContext }

trait Cache[A] {
    protected def isCached(context: Context): Boolean
    protected def cache(context: Context): Boolean
    protected def retrieve(context: Context): A

    def get(implicit ctx: Context, ec: ExecutionContext): Future[A] = if (isCached(ctx)) {
        Future.successful(retrieve(ctx))
    } else scala.concurrent.future {
        cache(ctx)
        retrieve(ctx)
    }
}
