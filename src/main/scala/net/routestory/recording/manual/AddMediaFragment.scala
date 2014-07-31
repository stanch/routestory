package net.routestory.recording.manual

import java.io.File

import android.app.Activity
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
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
  lazy val photoFile = File.createTempFile("photo", ".jpg", getActivity.getExternalCacheDir)
  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))
  val requestCodePhoto = 0

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    def clicker(factory: Ui[DialogFragment], tag: String) =
      factory.map(_.show(getChildFragmentManager, tag))

    val cameraClicker = Ui {
      val intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
      intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile))
      startActivityForResult(intent, requestCodePhoto)
    }

    val buttons = Seq(
      (R.drawable.ic_action_camera, "Take a picture", cameraClicker),
      (R.drawable.ic_action_view_as_list, "Add a text note", clicker(f[AddTextNote].factory, Tag.noteDialog)),
      (R.drawable.ic_action_mic, "Make a voice note", clicker(f[AddVoiceNote].factory, Tag.voiceDialog))
    )

    val listable = Listable.tr {
      l[HorizontalLinearLayout](
        w[ImageView],
        w[TextView] <~ TextTweaks.large
      ) <~ padding(top = 12 dp, bottom = 12 dp, left = 8 dp)
    } { button: (Int, String, Ui[Unit]) ⇒
      Transformer {
        case img: ImageView ⇒ img <~ ImageTweaks.res(button._1)
        case txt: TextView ⇒ txt <~ text(button._2)
        case l @ Transformer.Layout(_*) ⇒ l <~ On.click(button._3)
      }
    }

    w[ListView] <~ listable.listAdapterTweak(buttons)
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == requestCodePhoto && resultCode == Activity.RESULT_OK) {
      typewriter.foreach(_ ! Typewriter.Element(Story.Photo(photoFile)))
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
  var mMediaRecorder: MediaRecorder = null
  var mRecording = false
  var mRecorded = false
  var mStartStop = slot[Button]
  lazy val mOutputPath = File.createTempFile("voice", ".mp4", getActivity.getExternalCacheDir).getAbsolutePath

  def start() = {
    mMediaRecorder = new MediaRecorder {
      setAudioSource(AudioSource.MIC)
      setOutputFormat(OutputFormat.MPEG_4)
      setAudioEncoder(AudioEncoder.AAC)
      setOutputFile(mOutputPath)
    }
    try {
      mMediaRecorder.prepare()
      mMediaRecorder.start()
      mRecording = true
      mRecorded = true
      runUi {
        mStartStop <~ text("Stop recording")
      }
    } catch {
      case e: Throwable ⇒ e.printStackTrace()
    }
  }

  def stop() = {
    mMediaRecorder.stop()
    mMediaRecorder.release()
    mMediaRecorder = null
    mRecording = false
    runUi {
      mStartStop <~ text("Click if you want to try again")
    }
  }

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    w[Button] <~ text("Start recording") <~ wire(mStartStop) <~ On.click(Ui {
      if (!mRecording) start() else stop()
    })
  } <~ positiveOk(Ui {
    if (mRecording) {
      stop()
    }
    if (mRecorded) {
      typewriter.foreach(_ ! Typewriter.Element(Story.VoiceNote(new File(mOutputPath))))
    }
  }) <~ negativeCancel(Ui {
    if (mRecording) {
      stop()
    }
  })).create()
}
