package net.routestory.editing

import akka.actor.{ TypedProps, TypedActor, Props, Actor }
import android.os.Bundle
import android.support.v7.widget.CardView
import android.text.InputType
import android.view.{ KeyEvent, View, ViewGroup, LayoutInflater }
import android.widget.TextView.OnEditorActionListener
import android.widget._
import macroid.{ Ui, Tweak }
import macroid.akkafragments.{ FragmentActor, AkkaFragment }
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.contrib.{ TextTweaks, LpTweaks }
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

  def grabMeta = Ui {
    val meta = Story.Meta.fromStrings(
      title.get.getText.toString,
      description.get.getText.toString,
      tags.get.getText.toString
    )
    actor.foreach(_ ! Metadata.Update(meta))
  }

  def cardWithMargin(w: Ui[View]) =
    l[LinearLayout](l[CardView](w) <~ Styles.card <~ Styles.p8dding <~ LpTweaks.matchParent) <~
      padding(top = 8 dp, left = 8 dp, right = 8 dp)

  def hook = On.focusChange[EditText](grabMeta)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    l[ScrollView](
      l[VerticalLinearLayout](
        cardWithMargin(
          l[VerticalLinearLayout](
            w[TextView] <~
              text("Title") <~ Styles.header,
            w[EditText] <~ wire(title) <~ LpTweaks.matchWidth <~ hook <~
              inputType(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) <~
              Tweak[EditText](_.setHint("Untitled"))
          )
        ),

        cardWithMargin(
          l[VerticalLinearLayout](
            w[TextView] <~
              text("Description") <~ Styles.header,
            w[EditText] <~ wire(description) <~ LpTweaks.matchWidth <~ hook <~
              inputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE)
          )
        ),

        cardWithMargin(
          l[VerticalLinearLayout](
            w[TextView] <~
              text("Tags") <~ Styles.header,
            w[MultiAutoCompleteTextView] <~ wire(tags) <~ LpTweaks.matchWidth <~ hook <~
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
        .map(Listable.text(TextTweaks.medium + Styles.p8dding).listAdapter)
        .map(multiAutoCompleteAdapterTweak)
    }
  }
}

object Metadata {
  def props = Props(new Metadata)

  case class Update(meta: Story.Meta)
}

class Metadata extends FragmentActor[MetadataFragment] {
  import Metadata._
  import Editor._
  import FragmentActor._

  lazy val editor = context.actorSelection("../editor")

  def receive = receiveUi andThen {
    case AttachUi(_) ⇒
      editor ! Editor.Remind

    case Init(story) ⇒
      withUi(_.viewMeta(story.meta))

    case Update(meta) ⇒
      editor ! Editor.Meta(meta)

    case _ ⇒
  }
}
