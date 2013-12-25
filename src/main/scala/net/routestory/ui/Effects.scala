package net.routestory.ui

import org.macroid.Snails
import scala.concurrent.ExecutionContext

object Effects extends Snails {
  def fadeIn(implicit ec: ExecutionContext) = Snails.fadeIn(400)
  def fadeOut(implicit ec: ExecutionContext) = Snails.fadeOut(400)
}
