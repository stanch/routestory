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
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.HorizontalLinearLayout
import macroid.util.Ui
import macroid.viewable.{ FillableViewable, FillableViewableAdapter }
import macroid.{ IdGeneration, Transformer, Tweak }
import net.routestory.R
import net.routestory.recording.{ RecordActivity, Typewriter }
import net.routestory.ui.RouteStoryFragment
import net.routestory.ui.Styles._
import org.macroid.akkafragments.AkkaFragment

class AddMediaFragment extends RouteStoryFragment with IdGeneration with AkkaFragment {
  lazy val photoFile = File.createTempFile("photo", ".jpg", getActivity.getExternalCacheDir)
  lazy val typewriter = actorSystem.actorSelection("/user/typewriter")
  val actor = None

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    def clicker(factory: Ui[DialogFragment], tag: String) =
      factory.map(_.show(getChildFragmentManager, tag))

    val cameraClicker = Ui {
      val intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
      intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile))
      startActivityForResult(intent, RecordActivity.REQUEST_CODE_TAKE_PICTURE)
    }

    val buttons = Seq(
      (R.drawable.photo, "Take a picture", cameraClicker),
      (R.drawable.text_note, "Add a text note", clicker(f[AddTextNote].factory, Tag.noteDialog)),
      (R.drawable.voice_note, "Make a voice note", clicker(f[AddVoiceNote].factory, Tag.voiceDialog))
    )

    val adapter = FillableViewableAdapter(buttons)(FillableViewable.tr(
      l[HorizontalLinearLayout](
        w[ImageView],
        w[TextView] <~ TextSize.large
      ) <~ padding(top = 12 dp, bottom = 12 dp, left = 8 dp)) { button ⇒
        Transformer {
          case img: ImageView ⇒ img <~ Tweak[ImageView](_.setImageResource(button._1))
          case txt: TextView ⇒ txt <~ text(button._2)
          case l @ Transformer.Layout(_*) ⇒ l <~ On.click(button._3)
        }
      })

    w[ListView] <~ adaptr(adapter)
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == RecordActivity.REQUEST_CODE_TAKE_PICTURE) {
      if (resultCode == Activity.RESULT_OK) {
        typewriter ! Typewriter.Photo(photoFile)
      }
    }
  }
}

class AddSomething extends DialogFragment with RouteStoryFragment with AkkaFragment {
  lazy val typewriter = actorSystem.actorSelection("/user/typewriter")
  lazy val cartographer = actorSystem.actorSelection("/user/cartographer")
  val actor = None
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
      typewriter ! Typewriter.TextNote(text)
    }
  }) <~ negativeCancel(Ui.nop)).create()
}

class AddVoiceNote extends AddSomething {
  var mMediaRecorder: MediaRecorder = null
  var mRecording = false
  var mRecorded = false
  var mStartStop = slot[Button]
  lazy val mOutputPath = File.createTempFile("voice", ".mp4", getActivity.getExternalCacheDir).getAbsolutePath

  def start() {
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

  def stop() {
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
      typewriter ! Typewriter.VoiceNote(new File(mOutputPath))
      //activity.addVoice(mOutputPath)
    }
  }) <~ negativeCancel(Ui {
    if (mRecording) {
      stop()
    }
  })).create()
}
