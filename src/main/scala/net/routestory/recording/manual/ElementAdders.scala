package net.routestory.recording.manual

import java.io.File

import akka.util.Timeout
import android.content.{ DialogInterface, Intent }
import android.media.MediaRecorder
import android.os.{ Environment, Bundle }
import android.support.v4.app.{ DialogFragment, FragmentManager, Fragment }
import android.view.Gravity
import android.widget.{ LinearLayout, TextView, ImageView, EditText }
import macroid.FullDsl._
import macroid.contrib.{ LpTweaks, TextTweaks, ImageTweaks }
import macroid.contrib.Layouts.HorizontalLinearLayout
import macroid.{ Tweak, FragmentManagerContext, ActivityContext, Ui }
import net.routestory.R
import net.routestory.data.Story
import net.routestory.recording.logged.Dictaphone
import net.routestory.recording.{ Typewriter, RecordFragment, RecordActivity }
import net.routestory.ui.RouteStoryFragment
import net.routestory.util.TakePhotoActivity
import scala.concurrent.ExecutionContext.Implicits.global
import akka.pattern.ask

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
}

class AdderDialog extends DialogFragment with RouteStoryFragment with RecordFragment {
  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))
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
  lazy val dictaphone = actorSystem.map(_.actorSelection("/user/dictaphone"))

  var mediaRecorder: Option[Future[MediaRecorder]] = None

  lazy val voiceNoteFile = {
    val root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "RouteStory")
    root.mkdirs()
    File.createTempFile("voice-note", ".mp4", root)
  }

  def stop = Ui {
    dictaphone.foreach(_ ! Dictaphone.SwitchOn)
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
      dictaphone.flatMap(_ ? Dictaphone.SwitchOff)
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
