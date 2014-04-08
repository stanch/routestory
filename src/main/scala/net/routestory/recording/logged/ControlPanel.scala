package net.routestory.recording.logged

import net.routestory.ui.RouteStoryFragment
import akka.actor.Props
import macroid.FullDsl._
import android.view.{ ViewGroup, LayoutInflater }
import android.os.Bundle
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import android.widget.CheckBox
import org.macroid.akkafragments.{ FragmentActor, AkkaFragment }

class ControlPanelFragment extends RouteStoryFragment with AkkaFragment {
  lazy val cartographer = actorSystem.actorSelection("/user/cartographer")
  val actor = None

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
