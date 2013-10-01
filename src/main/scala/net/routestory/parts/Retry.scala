package net.routestory.parts

import retry.Backoff
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Retry {
  implicit val success = new retry.Success[Boolean](x ⇒ x)
  import retry.Defaults.timer
  def backoff(max: Int, delay: Duration = 5 seconds)(f: ⇒ Future[Boolean]) =
    Backoff(max = max, delay = delay)(() ⇒ f)
}
