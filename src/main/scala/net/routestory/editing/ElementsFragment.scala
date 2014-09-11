package net.routestory.editing

import akka.actor.Props
import android.widget._
import macroid.FullDsl._
import macroid.Ui
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import net.routestory.data.{ Timed, Story }
import net.routestory.ui.{ RouteStoryFragment, StaggeredFragment }
import net.routestory.viewable.{ ElementEditorListable, CardListable }

class ElementsFragment extends RouteStoryFragment with AkkaFragment with StaggeredFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/elemental"))
  lazy val editor = actorSystem.actorSelection("/user/editor")

  def viewChapter(chapter: Story.Chapter) = {
    val listable = CardListable.cardListable(new ElementEditorListable(chapter).elementEditorListable)
    val elements = chapter.knownElements.map(e ⇒ ElementEditor(e)(editor))

    grid <~ listable.listAdapterTweak(elements)
  }

  def removeElement(element: Timed[Story.KnownElement]) = Ui {
    grid.flatMap(g ⇒ Option(g.getAdapter))
      .map(_.asInstanceOf[ArrayAdapter[ElementEditor]])
      .map { a ⇒
        a.remove(ElementEditor(element)(editor))
        a.notifyDataSetChanged()
      }
  }
}

object Elemental {
  def props = Props(new Elemental)
}

class Elemental extends FragmentActor[ElementsFragment] {
  import FragmentActor._
  import Editor._

  lazy val editor = context.actorSelection("../editor")

  def receive = receiveUi andThen {
    case AttachUi(_) ⇒
      editor ! Editor.Remind

    case Init(story) ⇒
      // TODO: this only works when we have 1 chapter!
      withUi(_.viewChapter(story.chapters.head))

    case RemoveElement(element) ⇒
      withUi(_.removeElement(element))

    case _ ⇒
  }
}
