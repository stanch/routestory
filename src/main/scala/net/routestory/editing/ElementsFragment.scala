package net.routestory.editing

import akka.actor.Props
import android.view.{ ViewGroup, View }
import android.widget.{ ImageView, LinearLayout, Button, AdapterView }
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
import net.routestory.viewable.{ CardListable, StoryElementListable, TimedListable }
import android.view.ViewGroup.LayoutParams._
import net.routestory.R

class ElementsFragment extends RouteStoryFragment with AkkaFragment with StaggeredFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/elemental"))
  lazy val editor = actorSystem.actorSelection("/user/editor")

  def removeElement(element: Timed[Story.KnownElement]) =
    dialog("Do you want to delete this element?") <~
      positiveOk(Ui(editor ! Editor.RemoveElement(element))) <~
      negativeCancel(Ui.nop) <~
      speak

  def viewChapter(chapter: Story.Chapter) = {
    // TODO: this should be index-based!
    val buttons = Listable[Timed[Story.KnownElement]].tw {
      w[ImageView] <~ ImageTweaks.res(R.drawable.ic_action_discard)
    } { element ⇒
      On.click(removeElement(element))
    }

    val cards = CardListable.cardListable(
      new TimedListable(chapter).timedListable(
        StoryElementListable.storyElementListable))

    val listable = Listable.combine(buttons, cards) { (l1, l2) ⇒
      l[HorizontalLinearLayout](l2 <~ lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT, 1.0f), l1)
    }.contraMap[Timed[Story.KnownElement]](t ⇒ (t, t))

    grid <~ listable.listAdapterTweak(chapter.knownElements) <~
      FuncOn.itemClick[StaggeredGridView] { (_: AdapterView[_], _: View, index: Int, _: Long) ⇒
        ElementPager.show(
          Clustering.Leaf(chapter.knownElements(index), index, chapter, ()), _ ⇒ ()
        )
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

    case _ ⇒
  }
}
