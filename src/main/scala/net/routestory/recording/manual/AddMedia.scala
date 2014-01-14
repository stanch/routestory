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
import android.view.Gravity
import android.widget.{ ListAdapter ⇒ _, _ }

import akka.pattern.ask
import akka.util.Timeout
import com.google.android.gms.maps.model.LatLng
import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._
import org.macroid.contrib.ListAdapter
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import org.macroid.util.{ Effector, Thunk }
import rx.{ Rx, Var }

import net.routestory.R
import net.routestory.external.Foursquare
import net.routestory.recording.{ Cartographer, RecordActivity, Typewriter }
import net.routestory.ui.{ Effects, RouteStoryFragment }
import net.routestory.ui.Styles._
import net.routestory.util.Implicits._
import org.macroid.{ IdGeneration, Tweak, Transformer, Layout }

class AddMedia extends DialogFragment with RouteStoryFragment with IdGeneration {
  lazy val photoUrl = File.createTempFile("photo", ".jpg", getActivity.getExternalCacheDir).getAbsolutePath

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val activity = getActivity.asInstanceOf[RecordActivity]

    def clicker(factory: Thunk[DialogFragment], tag: String) = Thunk {
      dismiss()
      factory().show(activity.getSupportFragmentManager, tag)
    }

    val cameraClicker = Thunk {
      val intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
      intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(photoUrl)))
      startActivityForResult(intent, RecordActivity.REQUEST_CODE_TAKE_PICTURE)
    }

    val buttons = List(
      (R.drawable.photo, "Take a picture", cameraClicker),
      (R.drawable.text_note, "Add a text note", clicker(f[AddTextNote].factory, Tag.noteDialog)),
      (R.drawable.voice_note, "Make a voice note", clicker(f[AddVoiceNote].factory, Tag.voiceDialog)),
      (R.drawable.heart, "Record heartbeat", clicker(f[AddHeartbeat].factory, Tag.pulseDialog)),
      (R.drawable.foursquare, "Mention a venue", clicker(f[AddVenue].factory, Tag.fsqDialog))
    )

    val adapter = ListAdapter(buttons)(
      l[HorizontalLinearLayout](
        w[ImageView],
        w[TextView] ~> TextSize.large
      ) ~> padding(top = 12 dp, bottom = 12 dp, left = 8 dp),
      button ⇒ Transformer {
        case img: ImageView ⇒ img ~> (tweak(W[ImageView]) ~ (_.setImageResource(button._1)))
        case txt: TextView ⇒ txt ~> text(button._2)
        case l @ Layout(_*) ⇒ l ~> ThunkOn.click(button._3)
      }
    )

    val view = w[ListView] ~> (tweak doing (_.setAdapter(adapter)))
    new AlertDialog.Builder(activity).setView(view).create()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == RecordActivity.REQUEST_CODE_TAKE_PICTURE) {
      dismiss()
      val activity = getActivity.asInstanceOf[RecordActivity]
      if (resultCode == Activity.RESULT_OK) {
        activity.typewriter ! Typewriter.Photo(photoUrl)
      }
    }
  }
}

class AddSomething extends DialogFragment with RouteStoryFragment {
  lazy val activity = getActivity.asInstanceOf[RecordActivity]
}

class AddVenue extends AddSomething {
  implicit val self = this
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val list = w[ListView]
    val progress = w[ProgressBar](null, android.R.attr.progressBarStyleLarge)
    async {
      // TODO: properly work with Option?
      implicit val timeout = Timeout(4000)
      val location: LatLng = await((activity.cartographer ? Cartographer.QueryLast).mapTo[Option[LatLng]]).get
      val data = await(Foursquare.NeedNearbyVenues(location).go)
      await(progress ~@> Effects.fadeOut)
      val adapter = ListAdapter.text(data)(
        TextSize.large + padding(all = 4 sp),
        venue ⇒ text(venue.name) + On.click {
          activity.typewriter ! venue
          dismiss()
        }
      )
      list ~> Tweak[ListView](_.setAdapter(adapter))
    } onFailure {
      case t ⇒ t.printStackTrace()
    }
    new AlertDialog.Builder(activity) {
      setView(l[FrameLayout](list, progress))
      setNegativeButton(android.R.string.cancel, ())
    }.create()
  }
}

class AddTextNote extends AddSomething {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val input = new EditText(activity) {
      setHint(R.string.message_typenotehere)
      setMinLines(5)
      setGravity(Gravity.TOP)
    }
    new AlertDialog.Builder(activity) {
      setView(input)
      setPositiveButton(android.R.string.ok, Some(input.getText.toString).filter(!_.isEmpty).foreach { text ⇒
        activity.typewriter ! Typewriter.TextNote(text)
      })
      setNegativeButton(android.R.string.cancel, ())
    }.create()
  }
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
      mStartStop ~> text("Stop recording")
    } catch {
      case e: Throwable ⇒ e.printStackTrace()
    }
  }

  def stop() {
    mMediaRecorder.stop()
    mMediaRecorder.release()
    mMediaRecorder = null
    mRecording = false
    mStartStop ~> text("Click if you want to try again")
  }

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val view = w[Button] ~> text("Start recording") ~> wire(mStartStop) ~> On.click {
      if (!mRecording) start() else stop()
    }

    new AlertDialog.Builder(activity) {
      setView(view)
      setPositiveButton(android.R.string.ok, {
        if (mRecording) {
          stop()
        }
        if (mRecorded) {
          //activity.addVoice(mOutputPath)
        }
      })
      setNegativeButton(android.R.string.cancel, {
        if (mRecording) {
          stop()
        }
      })
    }.create()
  }
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

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val layout = l[VerticalLinearLayout](
      w[TextView] ~> text(R.string.message_pulsehowto) ~> TextSize.medium,
      w[TextView] ~> beats.map(b ⇒ text(s"BPM: $b")),
      w[Button] ~> text("TAP") ~> On.click {
        taps.update(System.currentTimeMillis() :: taps().take(4))
      } ~> (tweak doing { x ⇒
        x.setHeight(100 dp)
        x.setGravity(Gravity.CENTER)
      })
    )

    new AlertDialog.Builder(activity) {
      setView(layout)
      setPositiveButton(android.R.string.ok, Some(beats.now).filter(_ > 0).foreach { bpm ⇒
        activity.typewriter ! Typewriter.Heartbeat(bpm)
      })
      setNegativeButton(android.R.string.cancel, ())
    }.create()
  }
}

