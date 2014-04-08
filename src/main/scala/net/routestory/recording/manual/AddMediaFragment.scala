package net.routestory.recording.manual

import java.io.File

import scala.async.Async.{ async, await }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.dynamics

import android.app.{ Activity, AlertDialog, Dialog }
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.DialogFragment
import android.view.{ View, ViewGroup, LayoutInflater, Gravity }
import android.widget._

import akka.pattern.ask
import akka.util.Timeout
import macroid.FullDsl._
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.util.{ Ui, Effector }
import rx.{ Rx, Var }

import net.routestory.R
import net.routestory.recording.{ RecordActivity, Typewriter }
import net.routestory.ui.{ Effects, RouteStoryFragment }
import net.routestory.ui.Styles._
import net.routestory.util.Implicits._
import macroid.{ IdGeneration, Tweak, Transformer, Layout }
import net.routestory.util.FragmentData
import akka.actor.{ ActorSystem, ActorRef }
import macroid.viewable.{ FillableViewableAdapter, FillableViewable }
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
      (R.drawable.voice_note, "Make a voice note", clicker(f[AddVoiceNote].factory, Tag.voiceDialog)),
      (R.drawable.heart, "Record heartbeat", clicker(f[AddHeartbeat].factory, Tag.pulseDialog))
    )

    val adapter = FillableViewableAdapter(buttons)(FillableViewable.tr(
      l[HorizontalLinearLayout](
        w[ImageView],
        w[TextView] <~ TextSize.large
      ) <~ padding(top = 12 dp, bottom = 12 dp, left = 8 dp)) { button ⇒
        Transformer {
          case img: ImageView ⇒ img <~ Tweak[ImageView](_.setImageResource(button._1))
          case txt: TextView ⇒ txt <~ text(button._2)
          case l @ Layout(_*) ⇒ l <~ On.click(button._3)
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

class AddHeartbeat extends AddSomething {
  val taps = Var(List[Long]())
  val beats = Rx {
    if (taps().length < 5) 0 else {
      val interval = taps().sliding(2).map { case List(a, b) ⇒ a - b }.sum.toInt / 4
      60000 / interval
    }
  }

  var refs: List[Any] = Nil
  implicit object rxEffector extends Effector[Rx] {
    def foreach[A](fa: Rx[A])(f: A ⇒ Any) = {
      refs ::= fa.foreach(f andThen (_ ⇒ ()))
    }
  }

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    l[VerticalLinearLayout](
      w[TextView] <~ text(R.string.message_pulsehowto) <~ TextSize.medium,
      w[TextView] <~ beats.map(b ⇒ text(s"BPM: $b")),
      w[Button] <~ text("TAP") <~ On.click(Ui {
        taps.update(System.currentTimeMillis() :: taps().take(4))
      }) <~ Tweak[Button] { x ⇒
        x.setHeight(100 dp)
        x.setGravity(Gravity.CENTER)
      }
    )
  } <~ positiveOk(Ui {
    Some(beats.now).filter(_ > 0).foreach { bpm ⇒
      typewriter ! Typewriter.Heartbeat(bpm)
    }
  }) <~ negativeCancel(Ui.nop)).create()
}

