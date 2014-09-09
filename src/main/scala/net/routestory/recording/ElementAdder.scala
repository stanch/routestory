package net.routestory.recording

import java.io.File

import akka.actor.ActorSelection
import akka.util.Timeout
import android.content.{ Context, DialogInterface, Intent }
import android.media.MediaRecorder
import android.os.{ Vibrator, Environment, Bundle }
import android.support.v4.app.{ DialogFragment, FragmentManager, Fragment }
import android.view.{ View, Gravity }
import android.widget._
import macroid.FullDsl._
import macroid.contrib.{ LpTweaks, TextTweaks, ImageTweaks }
import macroid.contrib.Layouts.{ VerticalLinearLayout, HorizontalLinearLayout }
import macroid.viewable.{ SlottedListable, Listable }
import macroid._
import net.routestory.R
import net.routestory.browsing.story.ElementViewer
import net.routestory.data.Story
import net.routestory.data.Story.KnownElement
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.{ Preferences, TakePhotoActivity }
import net.routestory.viewable.{ StoryElementListable, TimedListable, CardListable }
import scala.concurrent.ExecutionContext.Implicits.global
import akka.pattern.ask
import scala.concurrent.duration._
import android.view.ViewGroup.LayoutParams._

import scala.concurrent.Future

sealed trait ElementAdder {
  def onClick: Ui[Any]
}

object ElementAdder {
  case class Photo(implicit ctx: ActivityContext) extends ElementAdder {
    def onClick = Ui {
      val intent = new Intent(ctx.get, classOf[TakePhotoActivity])
      ctx.get.startActivityForResult(intent, RecordActivity.requestCodeTakePhoto)
    }
  }

  case class TextNote(implicit ctx: ActivityContext, manager: FragmentManagerContext[Fragment, FragmentManager]) extends ElementAdder {
    def onClick = f[TextNoteDialog].factory
      .map(_.show(manager.get, "addTextNote"))
  }

  case class VoiceNote(implicit ctx: ActivityContext, manager: FragmentManagerContext[Fragment, FragmentManager]) extends ElementAdder {
    def onClick = f[VoiceNoteDialog].factory
      .map(_.show(manager.get, "addVoiceNote"))
  }

  case class AmbientSound(dictaphone: Future[ActorSelection])(implicit ctx: ActivityContext, appCtx: AppContext, manager: FragmentManagerContext[Fragment, FragmentManager]) extends ElementAdder {
    implicit val timeout = Timeout(2 seconds)

    def queryState = dictaphone.flatMap(_ ? Dictaphone.QueryState).mapTo[Boolean]
    val state = rx.Var(queryState)

    def showHint = {
      val vibrator = appCtx.get.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]
      if (vibrator.hasVibrator && Preferences.undefined("explainedDictaphone")) {
        dialog("Your device will vibrate every time the sound recording starts.") <~
          positive("Got it!")(Ui(Preferences.define("explainedDictaphone"))) <~
          speak
      } else Ui.nop
    }

    var numberPicker = slot[NumberPicker]

    def showSettings = dialog {
      l[HorizontalLinearLayout](
        w[TextView] <~
          text("Automatically record sound every") <~
          TextTweaks.large <~
          Tweak[TextView](_.setGravity(Gravity.CENTER_VERTICAL)) <~
          lp[LinearLayout](WRAP_CONTENT, MATCH_PARENT, 1.0f) <~
          padding(left = 8 dp),
        w[NumberPicker] <~ wire(numberPicker) <~ Tweak[NumberPicker] { x ⇒
          x.setMinValue(0)
          x.setMaxValue(3)
          x.setValue(2)
          x.setDisplayedValues(Array("1 minute", "3 minutes", "5 minutes", "10 minutes"))
        } <~ lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT, 1.0f)
      )
    } <~ positiveOk(showHint ~ Ui {
      dictaphone
        .flatMap(_ ? Dictaphone.SwitchOn(Array(1, 3, 5, 10)(numberPicker.get.getValue)))
        .foreach(_ ⇒ state.update(queryState))
    }) <~ negativeCancel(Ui.nop) <~ speak

    def onClick = Ui {
      state.now.mapUi { s ⇒
        if (s) {
          dictaphone
            .flatMap(_ ? Dictaphone.SwitchOff)
            .foreach(_ ⇒ state.update(queryState))
        } else {
          runUi(showSettings)
        }
      }
    }
  }

  case class Suggestion(element: Story.KnownElement)(suggester: Future[ActorSelection])(implicit ctx: ActivityContext, appCtx: AppContext) extends ElementAdder {
    def onClick = ElementViewer.show(element)
    def onAdd = Ui(suggester.foreach(_ ! Suggester.Add(element)))
    def onRemove = Ui(suggester.foreach(_ ! Suggester.Dismiss(element)))
  }
}

class AdderDialog extends DialogFragment with RouteStoryFragment with RecordFragment {
  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))
  lazy val dictaphone = actorSystem.map(_.actorSelection("/user/dictaphone"))
}

class TextNoteDialog extends AdderDialog {
  var input = slot[EditText]

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    w[EditText] <~ Tweak[EditText] { x ⇒
      x.setHint(R.string.message_typenotehere)
      x.setMinLines(5)
      x.setGravity(Gravity.TOP)
    } <~ wire(input)
  } <~ positiveOk(Ui {
    input.map(_.getText.toString).filter(_.nonEmpty).foreach { text ⇒
      typewriter.foreach(_ ! Typewriter.Element(Story.TextNote(text)))
    }
  }) <~ negativeCancel(Ui.nop)).create()
}

class VoiceNoteDialog extends AdderDialog {
  implicit val dictaphoneSwitchOffTimeout = Timeout(2000)

  var mediaRecorder: Option[Future[MediaRecorder]] = None

  lazy val voiceNoteFile = {
    val root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "RouteStory")
    root.mkdirs()
    File.createTempFile("voice-note", ".mp4", root)
  }

  def stop = Ui {
    dictaphone.foreach(_ ! Dictaphone.Resume)
    mediaRecorder.foreach(_.foreachUi { m ⇒
      m.stop()
      m.reset()
      m.release()
      mediaRecorder = None
    })
  }

  override def onCancel(dialog: DialogInterface) = {
    stop.run
    super.onCancel(dialog)
  }

  override def onStart() = {
    super.onStart()
    mediaRecorder = Some {
      dictaphone.flatMap(_ ? Dictaphone.Pause)
        .mapUi(_ ⇒ new MediaRecorder {
          setAudioSource(AudioSource.MIC)
          setOutputFormat(OutputFormat.MPEG_4)
          setAudioEncoder(AudioEncoder.AAC)
          setOutputFile(voiceNoteFile.getAbsolutePath)
          prepare()
          start()
        })
    }
  }

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    l[HorizontalLinearLayout](
      w[ImageView] <~ ImageTweaks.res(R.drawable.ic_action_mic),
      w[TextView] <~ TextTweaks.large <~ text("Recording...")
    ) <~ LpTweaks.matchParent <~
      Tweak[LinearLayout](_.setGravity(Gravity.CENTER)) <~
      padding(top = 12 dp)
  } <~ positive("Finish")(stop ~ Ui {
    typewriter.foreach(_ ! Typewriter.Element(Story.VoiceNote(voiceNoteFile)))
  }) <~ negativeCancel(stop)).create()
}
