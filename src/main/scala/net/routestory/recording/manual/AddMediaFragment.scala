package net.routestory.recording.manual

import java.io.File

import akka.util.Timeout
import android.app.Activity
import android.content.{ DialogInterface, Intent }
import android.media.{ MediaScannerConnection, MediaRecorder }
import android.net.Uri
import android.os.{ Environment, Bundle }
import android.provider.MediaStore
import android.support.v4.app.DialogFragment
import android.util.Log
import android.view.{ Gravity, LayoutInflater, ViewGroup }
import android.widget._
import macroid.FullDsl._
import macroid.contrib.Layouts.HorizontalLinearLayout
import macroid.contrib.{ LpTweaks, ImageTweaks, TextTweaks }
import macroid.viewable.Listable
import macroid.{ IdGeneration, Transformer, Tweak, Ui }
import net.routestory.R
import net.routestory.data.Story
import net.routestory.recording.logged.Dictaphone
import net.routestory.recording.{ Typewriter, RecordFragment }
import net.routestory.ui.RouteStoryFragment
import akka.pattern.ask

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AddMediaFragment extends RouteStoryFragment with IdGeneration with RecordFragment {
  def photoFile = {
    val root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RouteStory")
    root.mkdirs()
    File.createTempFile("photo", ".jpg", root)
  }

  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    def clicker(factory: Ui[DialogFragment], tag: String) =
      factory.map(_.show(getChildFragmentManager, tag))

    val cameraClicker = Ui {
      activity.lastPhotoFile = Some(photoFile)
      val intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
      intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(activity.lastPhotoFile.get))
      getActivity.startActivityForResult(intent, activity.requestCodePhoto)
    }

    val buttons = Seq(
      (R.drawable.ic_action_camera, "Take a picture", cameraClicker),
      (R.drawable.ic_action_view_as_list, "Add a text note", clicker(f[AddTextNote].factory, Tag.noteDialog)),
      (R.drawable.ic_action_mic, "Make a voice note", clicker(f[AddVoiceNote].factory, Tag.voiceDialog))
    )

    val listable = Listable[(Int, String, Ui[Unit])].tr {
      l[HorizontalLinearLayout](
        w[ImageView],
        w[TextView] <~ TextTweaks.large
      ) <~ padding(top = 12 dp, bottom = 12 dp, left = 8 dp)
    }(button ⇒ Transformer {
      case img: ImageView ⇒ img <~ ImageTweaks.res(button._1)
      case txt: TextView ⇒ txt <~ text(button._2)
      case l @ Transformer.Layout(_*) ⇒ l <~ On.click(button._3)
    })

    w[ListView] <~ listable.listAdapterTweak(buttons)
  }
}

class AddSomething extends DialogFragment with RouteStoryFragment with RecordFragment {
  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))
}

class AddTextNote extends AddSomething {
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

class AddVoiceNote extends AddSomething {
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

  override def onDismiss(dialog: DialogInterface) = {
    stop.run
    super.onDismiss(dialog)
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
