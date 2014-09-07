package net.routestory.recording

import java.io.File

import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.view.{ Gravity, View, LayoutInflater, ViewGroup }
import android.widget._
import com.etsy.android.grid.StaggeredGridView
import macroid.FullDsl._
import macroid._
import macroid.akkafragments.FragmentActor
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.contrib.{ LpTweaks, TextTweaks }
import net.routestory.data.Story
import net.routestory.ui.{ Styles, Tweaks, RouteStoryFragment }
import net.routestory.viewable.{ StoryElementListable, CardListable, ElementAdderListable }

import scala.concurrent.ExecutionContext.Implicits.global

sealed trait ElementOrAdder
object ElementOrAdder {
  case class Element(element: Story.KnownElement) extends ElementOrAdder
  case class Adder(adder: ElementAdder) extends ElementOrAdder

  def listable(implicit ctx: ActivityContext, appCtx: AppContext) =
    (StoryElementListable.storyElementListable
      .contraMap[Element](_.element)
      .toParent[ElementOrAdder] orElse
      ElementAdderListable.adderListable
      .contraMap[Adder](_.adder)
      .toParent[ElementOrAdder]).toTotal
}

class AddMediaFragment extends RouteStoryFragment with IdGeneration with RecordFragment {
  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))
  lazy val suggester = actorSystem.map(_.actorSelection("/user/suggester"))

  var grid = slot[StaggeredGridView]
  var swiper = slot[SwipeRefreshLayout]

  def adders = List(
    ElementAdder.Photo(),
    ElementAdder.TextNote(),
    ElementAdder.VoiceNote(),
    ElementAdder.AmbientSound()
  ).map(ElementOrAdder.Adder)

  def showSuggestions(suggestions: List[Story.KnownElement], initial: Boolean = false) = {
    if (!initial) {
      typewriter.foreach(_ ! Typewriter.Suggestions(suggestions.length))
    }

    val listable = CardListable.cardListable(ElementOrAdder.listable)
    val stuff = adders ::: suggestions.map(ElementOrAdder.Element)

    val updateGrid = grid <~ listable.listAdapterTweak(stuff) <~
      FuncOn.itemClick[StaggeredGridView] { (_: AdapterView[_], _: View, index: Int, _: Long) ⇒
        stuff(index) match {
          case ElementOrAdder.Adder(adder) ⇒ adder.onClick
          case ElementOrAdder.Element(element) ⇒
            Ui(typewriter.foreach(_ ! Typewriter.Element(element)))
        }
      }

    updateGrid ~ (swiper <~ Tweaks.stopRefresh)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    l[SwipeRefreshLayout](
      w[StaggeredGridView] <~ Styles.grid <~ wire(grid)
    ) <~ Styles.swiper <~ wire(swiper) <~ On.refresh[SwipeRefreshLayout](Ui {
        suggester.foreach(_ ! Suggester.Update)
      })
  }

  override def onStart() = {
    super.onStart()
    if (grid.get.getAdapter == null) showSuggestions(Nil, initial = true).run
    suggester.foreach(_ ! FragmentActor.AttachUi(this))
  }

  override def onStop() = {
    super.onStop()
    suggester.foreach(_ ! FragmentActor.DetachUi(this))
  }
}

class AddEasiness extends AdderDialog {
  setCancelable(false)
  var rating = slot[RatingBar]

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    l[VerticalLinearLayout](
      w[TextView] <~ text("How easy was it?") <~
        TextTweaks.large <~ padding(all = 4 dp),
      w[RatingBar] <~ wire(rating) <~
        Tweak[RatingBar](_.setNumStars(5)) <~
        LpTweaks.wrapContent
    )
  } <~ positiveOk {
    Ui(typewriter.map(_ ! Typewriter.Easiness(rating.get.getRating))) ~~
      Ui(getActivity.asInstanceOf[RecordActivity].save.get) // fix eagerness!
  }).create()
}

class AddPhotoCaption extends AdderDialog {
  var input = slot[EditText]

  lazy val photoFile = new File(getArguments.getString("photoFile"))

  override def onCancel(dialog: DialogInterface) = {
    typewriter.foreach(_ ! Typewriter.Element(Story.Photo(None, photoFile)))
    super.onCancel(dialog)
  }

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    w[EditText] <~ Tweak[EditText] { x ⇒
      x.setHint("Type a caption here")
      x.setMinLines(5)
      x.setGravity(Gravity.TOP)
    } <~ wire(input)
  } <~ positiveOk(Ui {
    val cap = input.map(_.getText.toString).filter(_.nonEmpty)
    typewriter.foreach(_ ! Typewriter.Element(Story.Photo(cap, photoFile)))
  }) <~ negative("No caption")(Ui {
    typewriter.foreach(_ ! Typewriter.Element(Story.Photo(None, photoFile)))
  })).create()
}
