package net.routestory.editing

import akka.actor.Props
import android.view.{ ViewGroup, View }
import android.widget._
import com.etsy.android.grid.StaggeredGridView
import macroid.FullDsl._
import macroid.Ui
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.contrib.{ ImageTweaks, LpTweaks, BgTweaks }
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.viewable.Listable
import net.routestory.browsing.story.ElementPager
import net.routestory.data.{ Timed, Clustering, Story }
import net.routestory.ui.{ RouteStoryFragment, StaggeredFragment }
import net.routestory.viewable.{ ElementEditorListable, CardListable, StoryElementListable, TimedListable }
import android.view.ViewGroup.LayoutParams._
import net.routestory.R

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
