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
import net.routestory.ui.{ SwipingStaggeredFragment, Styles, Tweaks, RouteStoryFragment }
import net.routestory.util.Preferences
import net.routestory.viewable.{ CardListable, ElementAdderListable }

import scala.concurrent.ExecutionContext.Implicits.global

class AddMediaFragment extends RouteStoryFragment with IdGeneration with RecordFragment with SwipingStaggeredFragment {
  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))
  lazy val suggester = actorSystem.map(_.actorSelection("/user/suggester"))
  lazy val dictaphone = actorSystem.map(_.actorSelection("/user/dictaphone"))

  def adders = List(
    ElementAdder.Photo(),
    ElementAdder.TextNote(),
    ElementAdder.VoiceNote(),
    ElementAdder.AmbientSound(dictaphone)
  )

  def showSuggestions(suggestions: List[Story.KnownElement], initial: Boolean = false) = {
    if (!initial) {
      typewriter.foreach(_ ! Typewriter.Suggestions(suggestions.length))
    }

    val listable = CardListable.cardListable(ElementAdderListable.adderListable)
    val stuff = adders ::: suggestions.map(s ⇒ ElementAdder.Suggestion(s)(suggester))
    val updateGrid = grid <~ listable.listAdapterTweak(stuff)

    val showHint = if (!initial && Preferences.undefined("explainedSuggestions")) {
      dialog("We’ve got suggestions for you! Enrich your story and save some precious time by adding Foursquare venues or photos from Instagram and Flickr! Pull down to refresh the suggestions.") <~
        positive("Got it!")(Ui(Preferences.define("explainedSuggestions"))) <~
        speak
    } else Ui.nop

    updateGrid ~ (swiper <~ Tweaks.stopRefresh) ~ showHint
  }

  def hideSuggestion(suggestion: Story.KnownElement) = Ui {
    grid.flatMap(g ⇒ Option(g.getAdapter))
      .map(_.asInstanceOf[ArrayAdapter[ElementAdder]])
      .map { a ⇒
        a.remove(ElementAdder.Suggestion(suggestion)(suggester))
        a.notifyDataSetChanged()
      }
  }

  def refresh = Ui(suggester.foreach(_ ! Suggester.Update))

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
