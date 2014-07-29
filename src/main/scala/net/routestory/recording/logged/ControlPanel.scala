package net.routestory.recording.logged

import akka.actor.Props
import android.os.Bundle
import android.view.{ LayoutInflater, ViewGroup }
import android.widget.CheckBox
import macroid.FullDsl._
import macroid.akkafragments.FragmentActor
import macroid.contrib.Layouts.VerticalLinearLayout
import net.routestory.recording.RecordActivity
import net.routestory.ui.RouteStoryFragment

import scala.concurrent.ExecutionContext.Implicits.global

class ControlPanelFragment extends RouteStoryFragment {
  lazy val cartographer = getActivity.asInstanceOf[RecordActivity].actorSystem.future.map(_.actorSelection("/user/cartographer"))

  var dictaphoneOn = slot[CheckBox]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    l[VerticalLinearLayout](
      w[CheckBox] <~ wire(dictaphoneOn) <~ text("Dictaphone")
    )
  }
}

object ControlPanel {
  def props = Props(new ControlPanel)
}

class ControlPanel extends FragmentActor {
  def receive = receiveUi andThen {
    case _ ⇒
  }
}
