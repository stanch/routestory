package net.routestory.recording

import scala.language.dynamics
import android.support.v4.app.DialogFragment
import android.widget.{ ListAdapter ⇒ _, _ }
import android.os.Bundle
import android.media.MediaRecorder
import MediaRecorder._
import android.app.AlertDialog
import java.io.File
import android.view.{ ViewGroup, Gravity, View }
import net.routestory.R
import android.app.Dialog
import android.content.{ Context, DialogInterface, Intent }
import net.routestory.parts.{ Effects, RouteStoryFragment }
import android.app.Activity
import android.provider.MediaStore
import android.net.Uri
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.parts.Implicits._
import org.macroid._
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import org.macroid.util.{ Effector, Thunk }
import scala.collection.JavaConversions._
import org.macroid.contrib.ExtraTweaks
import org.codehaus.jackson.map.ObjectMapper
import rx.{ Rx, Var }
import scala.async.Async.{ async, await }
import com.google.android.gms.maps.model.LatLng
import net.routestory.external.Foursquare
import org.macroid.contrib.ListAdapter
import akka.pattern.ask
import akka.util.Timeout

class AddMediaDialogFragment extends DialogFragment with RouteStoryFragment {
  import AddMediaDialogFragment._
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
      (R.drawable.text_note, "Add a text note", clicker(f[TextNoteDialogFragment].factory, Tag.noteDialog)),
      (R.drawable.voice_note, "Make a voice note", clicker(f[VoiceDialogFragment].factory, Tag.voiceDialog)),
      (R.drawable.heart, "Record heartbeat", clicker(f[MeasurePulseDialogFragment].factory, Tag.pulseDialog)),
      (R.drawable.foursquare, "Mention a venue", clicker(f[FoursquareDialogFragment].factory, Tag.fsqDialog))
    )

    val view = w[ListView] ~> (_.setAdapter(new MediaListAdapter(buttons)))
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

object AddMediaDialogFragment {
  class MediaListAdapter(media: List[(Int, String, Thunk[Any])])(implicit ctx: Context) extends ArrayAdapter(ctx, 0, media) with LayoutDsl with MediaQueries with ExtraTweaks with BasicViewSearch {
    override def getView(position: Int, itemView: View, parent: ViewGroup): View = {
      val item = getItem(position)
      val v = Option(itemView) getOrElse {
        l[HorizontalLinearLayout](
          w[ImageView] ~> id(Id.image), w[TextView] ~> id(Id.text) ~> TextSize.large
        ) ~> padding(top = 12 dp, bottom = 12 dp, left = 8 dp)
      }
      findView[ImageView](v, Id.image) ~> (_.setImageResource(item._1))
      findView[TextView](v, Id.text) ~> text(item._2)
      v ~> ThunkOn.click(item._3)
    }
  }
}

class AddSomethingDialogFragment extends DialogFragment {
  lazy val activity = getActivity.asInstanceOf[RecordActivity]
}

class FoursquareDialogFragment extends AddSomethingDialogFragment with UiThreading with BasicViewSearch with MediaQueries with FragmentContext {
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
      list ~> (_.setAdapter(adapter))
    } onFailure {
      case t ⇒ t.printStackTrace()
    }
    new AlertDialog.Builder(activity) {
      setView(l[FrameLayout](list, progress))
      setNegativeButton(android.R.string.cancel, ())
    }.create()
  }
}

class TextNoteDialogFragment extends AddSomethingDialogFragment {
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

class VoiceDialogFragment extends AddSomethingDialogFragment with LayoutDsl with Tweaks with FragmentContext {
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

class MeasurePulseDialogFragment extends AddSomethingDialogFragment with LayoutDsl with MediaQueries with Tweaks with FragmentContext {
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
      } ~> { x ⇒
        x.setHeight(100 dp)
        x.setGravity(Gravity.CENTER)
      }
    )

    new AlertDialog.Builder(ctx) {
      setView(layout)
      setPositiveButton(android.R.string.ok, Some(beats.now).filter(_ > 0).foreach { bpm ⇒
        activity.typewriter ! Typewriter.Heartbeat(bpm)
      })
      setNegativeButton(android.R.string.cancel, ())
    }.create()
  }
}

