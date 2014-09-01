package net.routestory.browsing.story

import akka.actor.Props
import android.os.Bundle
import android.view.{ LayoutInflater, View, ViewGroup }
import android.widget.{ ScrollView, TextView }
import macroid.FullDsl._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.contrib.{ LpTweaks, TextTweaks }
import net.routestory.data.Story
import net.routestory.ui.RouteStoryFragment
import net.routestory.viewable.StoryPreviewListable
import org.apmem.tools.layouts.FlowLayout

class DetailsFragment extends RouteStoryFragment with AkkaFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/detailer"))

  var description = slot[TextView]
  var tags = slot[FlowLayout]

  def viewDetails(meta: Story.Meta) = {
    (description <~ meta.description.map(text) <~ show(meta.description.isDefined)) ~
      (Some(meta.tags).filter(_.nonEmpty) map { t ⇒
        tags <~ addViews(t.map(StoryPreviewListable.tag(_, None)), removeOld = true) + show
      } getOrElse {
        tags <~ hide
      })
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[ScrollView](
      l[VerticalLinearLayout](
        w[TextView] <~ LpTweaks.matchWidth <~
          TextTweaks.medium <~
          padding(bottom = 12 dp) <~
          wire(description),

        w[FlowLayout] <~ wire(tags)
      ) <~ padding(all = 8 dp)
    )
  }
}

object Detailer {
  def props = Props(new Detailer)
}

class Detailer extends FragmentActor[DetailsFragment] {
  import FragmentActor._
  import Coordinator._

  lazy val coordinator = context.actorSelection("../coordinator")

  def receive = receiveUi andThen {
    case AttachUi(_) ⇒
      coordinator ! Coordinator.RemindMeta

    case UpdateMeta(meta) ⇒
      withUi(_.viewDetails(meta))
  }
}
