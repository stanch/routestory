package net.routestory.parts

import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

object ExecutionContexts {
  implicit lazy val tight = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
}
