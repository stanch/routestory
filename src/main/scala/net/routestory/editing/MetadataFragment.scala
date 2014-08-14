package net.routestory.editing

import akka.actor.{ Props, Actor }
import android.os.Bundle
import android.support.v7.widget.CardView
import android.text.InputType
import android.view.{ View, ViewGroup, LayoutInflater }
import android.widget._
import macroid.{ Ui, Tweak }
import macroid.akkafragments.AkkaFragment
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.contrib.LpTweaks
import macroid.viewable.Listable
import net.routestory.data.Story
import net.routestory.ui.{ Styles, RouteStoryFragment }
import macroid.FullDsl._

import scala.concurrent.ExecutionContext.Implicits.global

class MetadataFragment extends RouteStoryFragment with AkkaFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/metadata"))

  var title = slot[EditText]
  var description = slot[EditText]
  var tags = slot[MultiAutoCompleteTextView]

  def inputType(t: Int) = Tweak[EditText](_.setInputType(t))

  def multiAutoCompleteAdapterTweak(adapter: ListAdapter with Filterable) =
    Tweak[MultiAutoCompleteTextView](_.setAdapter(adapter))

  def viewMeta(meta: Story.Meta) = {
    (title <~ meta.title.map(text)) ~
      (description <~ meta.description.map(text)) ~
      (tags <~ text(meta.tags.mkString(", ")))
  }

  def cardWithMargin(w: Ui[View]) =
    l[LinearLayout](l[CardView](w) <~ Styles.card <~ LpTweaks.matchParent) <~
      padding(top = 8 dp, left = 8 dp, right = 8 dp)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    l[ScrollView](
      l[VerticalLinearLayout](
        cardWithMargin(
          l[VerticalLinearLayout](
            w[TextView] <~
              text("Title") <~ Styles.header,
            w[EditText] <~ wire(title) <~ LpTweaks.matchWidth <~
              inputType(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE)
          )
        ),

        cardWithMargin(
          l[VerticalLinearLayout](
            w[TextView] <~
              text("Description") <~ Styles.header,
            w[EditText] <~ wire(description) <~ LpTweaks.matchWidth <~
              inputType(InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)
          )
        ),

        cardWithMargin(
          l[VerticalLinearLayout](
            w[TextView] <~
              text("Tags") <~ Styles.header,
            w[MultiAutoCompleteTextView] <~ wire(tags) <~ LpTweaks.matchWidth <~
              inputType(InputType.TYPE_CLASS_TEXT) <~ Tweak[MultiAutoCompleteTextView] { x ⇒
                x.setHint("Lisbon, nice weather, tour")
                x.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer)
              }
          )
        )
      )
    )
  }

  override def onStart() = {
    super.onStart()
    runUi {
      tags <~ app.webApi.tags.go
        .map(_.map(_.tag))
        .map(Listable.text(Tweak.blank).listAdapter)
        .map(multiAutoCompleteAdapterTweak)
    }
  }
}

object MetadataActor {
  def props = Props(new MetadataActor)
}

class MetadataActor extends Actor {
  def receive = {
    case _ ⇒
  }
}
