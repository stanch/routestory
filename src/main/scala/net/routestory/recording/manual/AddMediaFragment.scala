package net.routestory.recording.manual

import java.io.File

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection.OnScanCompletedListener
import android.media.{ MediaScannerConnection, MediaRecorder }
import android.net.Uri
import android.os.{ Environment, Bundle }
import android.provider.MediaStore
import android.support.v4.app.DialogFragment
import android.view.{ Gravity, LayoutInflater, ViewGroup }
import android.widget._
import macroid.FullDsl._
import macroid.contrib.Layouts.HorizontalLinearLayout
import macroid.contrib.{ ImageTweaks, ListTweaks, TextTweaks }
import macroid.viewable.Listable
import macroid.{ IdGeneration, Transformer, Tweak, Ui }
import net.routestory.R
import net.routestory.data.Story
import net.routestory.recording.{ Typewriter, RecordFragment }
import net.routestory.ui.RouteStoryFragment

import scala.concurrent.ExecutionContext.Implicits.global

class AddMediaFragment extends RouteStoryFragment with IdGeneration with RecordFragment {
  def photoFile = {
    val root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RouteStory")
    root.mkdirs()
    File.createTempFile("photo", ".jpg", root)
  }
  var lastPhotoFile: Option[File] = None

  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))
  lazy val dictaphone = actorSystem.map(_.actorSelection("/user/dictaphone"))
  val requestCodePhoto = 0

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    def clicker(factory: Ui[DialogFragment], tag: String) =
      factory.map(_.show(getChildFragmentManager, tag))

    val cameraClicker = Ui {
      lastPhotoFile = Some(photoFile)
      val intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
      intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(lastPhotoFile.get))
      startActivityForResult(intent, requestCodePhoto)
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

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == requestCodePhoto && resultCode == Activity.RESULT_OK) {
      lastPhotoFile foreach { file ⇒
        MediaScannerConnection.scanFile(getActivity.getApplicationContext, Array(file.getAbsolutePath), null, null)
        typewriter.foreach(_ ! Typewriter.Element(Story.Photo(file)))
      }
      lastPhotoFile = None
    }
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
    Some(input.get.getText.toString).filter(!_.isEmpty).foreach { text ⇒
      typewriter.foreach(_ ! Typewriter.Element(Story.TextNote(text)))
    }
  }) <~ negativeCancel(Ui.nop)).create()
}

class AddVoiceNote extends AddSomething {
  var mediaRecorder: MediaRecorder = null
  var recording = false
  var recorded = false
  var startStop = slot[Button]

  lazy val voiceNoteFile = {
    val root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "RouteStory")
    root.mkdirs()
    File.createTempFile("voice-note", ".mp4", root)
  }

  def start() = {
    mediaRecorder = new MediaRecorder {
      setAudioSource(AudioSource.MIC)
      setOutputFormat(OutputFormat.MPEG_4)
      setAudioEncoder(AudioEncoder.AAC)
      setOutputFile(voiceNoteFile.getAbsolutePath)
    }
    try {
      mediaRecorder.prepare()
      mediaRecorder.start()
      recording = true
      recorded = true
      runUi {
        startStop <~ text("Stop recording")
      }
    } catch {
      case e: Throwable ⇒ e.printStackTrace()
    }
  }

  def stop() = {
    mediaRecorder.stop()
    mediaRecorder.release()
    mediaRecorder = null
    recording = false
    runUi {
      startStop <~ text("Click if you want to try again")
    }
  }

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    w[Button] <~ text("Start recording") <~ wire(startStop) <~ On.click(Ui {
      if (!recording) start() else stop()
    })
  } <~ positiveOk(Ui {
    if (recording) {
      stop()
    }
    if (recorded) {
      typewriter.foreach(_ ! Typewriter.Element(Story.VoiceNote(voiceNoteFile)))
    }
  }) <~ negativeCancel(Ui {
    if (recording) {
      stop()
    }
  })).create()
}
