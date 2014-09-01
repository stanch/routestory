package net.routestory.recording.manual

import java.io.File

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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
import macroid.contrib.{ LpTweaks, ImageTweaks, ListTweaks, TextTweaks }
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
        var caption = slot[EditText]
        runUi {
          dialog {
            w[EditText] <~ Tweak[EditText] { x ⇒
              x.setHint("Type a caption here")
              x.setMinLines(5)
              x.setGravity(Gravity.TOP)
            } <~ wire(caption)
          } <~ positiveOk(Ui {
            val cap = caption.map(_.getText.toString).filter(_.nonEmpty)
            typewriter.foreach(_ ! Typewriter.Element(Story.Photo(cap, file)))
          }) <~ negative("No caption")(Ui {
            typewriter.foreach(_ ! Typewriter.Element(Story.Photo(None, file)))
          }) <~ speak
        }
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
    input.map(_.getText.toString).filter(_.nonEmpty).foreach { text ⇒
      typewriter.foreach(_ ! Typewriter.Element(Story.TextNote(text)))
    }
  }) <~ negativeCancel(Ui.nop)).create()
}

class AddVoiceNote extends AddSomething {
  val mediaRecorder = new MediaRecorder {
    setAudioSource(AudioSource.MIC)
    setOutputFormat(OutputFormat.MPEG_4)
    setAudioEncoder(AudioEncoder.AAC)
    setOutputFile(voiceNoteFile.getAbsolutePath)
    prepare()
    start()
  }

  lazy val voiceNoteFile = {
    val root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "RouteStory")
    root.mkdirs()
    File.createTempFile("voice-note", ".mp4", root)
  }

  def stop = Ui {
    mediaRecorder.stop()
    mediaRecorder.release()
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
