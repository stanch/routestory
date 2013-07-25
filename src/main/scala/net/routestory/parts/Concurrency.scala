package net.routestory.parts

import scala.util.continuations._
import scala.concurrent.{ Promise, Future }
import java.util.{ Observer, Observable }

object Concurrency {
  def observeUntil[A](observable: Observable)(pred: (Observable, Object) ⇒ Boolean) = shift { f: (Unit ⇒ Future[A]) ⇒
    val promise = Promise[A]()
    observable.addObserver(new Observer {
      override def update(o: Observable, data: Object) {
        if (pred(o, data)) {
          observable.deleteObserver(this)
          promise.completeWith(f())
        }
      }
    })
    promise.future
  }
}
