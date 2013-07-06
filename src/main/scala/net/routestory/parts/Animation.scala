package net.routestory.parts

import android.view.View
import android.view.animation.{AlphaAnimation, Animation}
import android.view.animation.Animation.AnimationListener
import scala.concurrent.{Promise, Future}
import scala.util.Success
import org.scaloid.common._

object Animation {
    implicit class RichAnimation(anim: Animation) {
        def runOn(view: View, hideOnFinish: Boolean = false): Future[Unit] = {
            val p = Promise[Unit]()
            anim.setAnimationListener(new AnimationListener {
                override def onAnimationStart(a: Animation) {}
                override def onAnimationRepeat(a: Animation) {}
                override def onAnimationEnd(a: Animation) {
                    if (hideOnFinish) view.setVisibility(View.GONE)
                    p.complete(Success(()))
                }
            })
            runOnUiThread {
                view.setVisibility(View.VISIBLE)
                view.startAnimation(anim)
            }
            p.future
        }

        def duration(d: Long): Animation = {
            anim.setDuration(d)
            anim
        }
    }
}