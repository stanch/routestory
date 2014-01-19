package net.routestory.recording.logged

import net.routestory.ui.RouteStoryFragment
import net.routestory.util.FragmentData
import akka.actor.{ Actor, ActorSystem }
import org.macroid.FullDsl._
import android.view.{ ViewGroup, LayoutInflater }
import android.os.Bundle
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import android.widget.CheckBox

class ControlPanelFragment extends RouteStoryFragment with FragmentData[ActorSystem] {
  lazy val cartographer = getFragmentData.actorSelection("/user/cartographer")

  var dictaphoneOn = slot[CheckBox]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    l[VerticalLinearLayout](
      w[CheckBox] ~> wire(dictaphoneOn) ~> text("Dictaphone")
    )
  }

  override def onStart() {
    super.onStart()
    //cartographer ! Cartographer.AttachUi(this)
  }

  override def onStop() {
    super.onStop()
    //cartographer ! Cartographer.DetachUi
  }
}

object ControlPanel {

}

class ControlPanel extends Actor {
  def receive = {
    case _ ⇒
  }
}
