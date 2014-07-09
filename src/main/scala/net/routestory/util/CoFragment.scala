package net.routestory.util

import java.util.concurrent.Executors

import android.support.v4.app.{FragmentActivity, Fragment}
import macroid.util.Ui

trait ActiveObject {
  private lazy val executor = Executors.newFixedThreadPool(1)
  def active(code: ⇒ Unit): Unit = {
    executor.submit(new Runnable {
      def run() = code
    })
  }
}

trait CoFragment[F <: Fragment] extends ActiveObject {
  private var ui: Option[F] = None

  def withUi(action: F ⇒ Ui[Any]) = ui foreach { u ⇒
    action(ui).run
  }

  def attachUi(f: F) = active {
    ui = Some(f)
  }

  def detachUi() = active {
    ui = None
  }
}

trait FragmentOf[A <: FragmentActivity] { self: Fragment ⇒
  def activity = getActivity.asInstanceOf[A]
}

trait ProxiedFragment[F <: Fragment] extends Fragment {
  def coFragment: CoFragment[F]

  abstract override def onStart() = {
    super.onStart()
    coFragment.attachUi(this)
  }

  abstract override def onStop() = {
    super.onStop()
    coFragment.detachUi()
  }
}