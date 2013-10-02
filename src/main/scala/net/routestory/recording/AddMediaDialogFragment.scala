package net.routestory.recording

import scala.language.dynamics
import android.support.v4.app.DialogFragment
import android.widget._
import android.os.Bundle
import android.media.MediaRecorder
import MediaRecorder._
import android.app.AlertDialog
import java.io.File
import android.view.{ ViewGroup, Gravity, View }
import net.routestory.R
import android.app.Dialog
import org.scaloid.common._
import android.content.{ Context, DialogInterface, Intent }
import net.routestory.parts.{ Effects, RouteStoryFragment }
import android.app.Activity
import android.provider.MediaStore
import android.net.Uri
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.parts.Implicits._
import org.macroid._
import net.routestory.parts.Styles._
import java.net.{ HttpURLConnection, URL }
import scala.concurrent.future
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, GravityGridLayout, VerticalLinearLayout }
import org.macroid.util.Thunk
import scala.collection.JavaConversions._
import org.macroid.contrib.ExtraTweaks
import org.codehaus.jackson.map.ObjectMapper
import java.util.Locale
import rx.{ Rx, Var }
import scala.async.Async.{ async, await }
import scala.Dynamic
import org.codehaus.jackson.JsonNode

class AddMediaDialogFragment extends DialogFragment with RouteStoryFragment {
  import AddMediaDialogFragment._
  lazy val mPhotoPath = File.createTempFile("photo", ".jpg", getActivity.getExternalCacheDir).getAbsolutePath

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val activity = getActivity.asInstanceOf[RecordActivity]

    def clicker(factory: Thunk[DialogFragment], tag: String) = async {
      await(activity.untrackAudio())
      Ui {
        dismiss()
        factory().show(activity.getSupportFragmentManager, tag)
      }
    }

    def cameraClicker = async {
      await(activity.untrackAudio())
      Ui {
        val intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(mPhotoPath)))
        startActivityForResult(intent, RecordActivity.REQUEST_CODE_TAKE_PICTURE)
      }
    }

    val buttons = List(
      (R.drawable.photo, "Take a picture", Thunk(cameraClicker)),
      (R.drawable.text_note, "Add a text note", Thunk(clicker(ff[NoteDialogFragment](), Tag.noteDialog))),
      (R.drawable.voice_note, "Make a voice note", Thunk(clicker(ff[VoiceDialogFragment](), Tag.voiceDialog))),
      (R.drawable.heart, "Record heartbeat", Thunk(clicker(ff[MeasurePulseDialogFragment](), Tag.pulseDialog))),
      (R.drawable.foursquare, "Mention a venue", Thunk(clicker(ff[FoursquareDialogFragment](), Tag.fsqDialog)))
    )

    val view = w[ListView] ~> (_.setAdapter(new MediaListAdapter(buttons)))
    new AlertDialog.Builder(activity).setView(view).create()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == RecordActivity.REQUEST_CODE_TAKE_PICTURE) {
      dismiss()
      val activity = getActivity.asInstanceOf[RecordActivity]
      activity.trackAudio()
      if (resultCode == Activity.RESULT_OK) {
        activity.addPhoto(mPhotoPath)
      }
    }
  }
}

object AddMediaDialogFragment {
  class MediaListAdapter(media: List[(Int, String, Thunk[Any])])(implicit ctx: Context) extends ArrayAdapter(ctx, 0, media) with LayoutDsl with ExtraTweaks with BasicViewSearch {
    override def getView(position: Int, itemView: View, parent: ViewGroup): View = {
      val item = getItem(position)
      val v = Option(itemView) getOrElse {
        l[HorizontalLinearLayout](
          w[ImageView] ~> id(Id.image), w[TextView] ~> id(Id.text) ~> TextSize.large
        ) ~> padding(top = (12 dip), bottom = (12 dip), left = (8 dip))
      }
      findView[ImageView](v, Id.image) ~> (_.setImageResource(item._1))
      findView[TextView](v, Id.text) ~> text(item._2)
      v ~> ThunkOn.click(item._3)
    }
  }
}

class AddSomethingDialogFragment extends DialogFragment {
  lazy val activity = getActivity.asInstanceOf[RecordActivity]
  lazy val coords = activity.mRouteManager.getEnd

  override def onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    activity.trackAudio()
  }
}

class FoursquareDialogFragment extends AddSomethingDialogFragment with Concurrency with BasicViewSearch with FragmentContext {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val client_id = "0TORHPL0MPUG24YGBVNINGV2LREZJCD0XBCDCBMFC0JPDO05"
    val client_secret = "SIPSHLBOLADA2HW3RT44GE14OGBDNSM0VPBN4MSEWH2E4VLN"
    val ll = "%f,%f".formatLocal(Locale.US, coords.latitude, coords.longitude)
    val urll = new URL(s"https://api.foursquare.com/v2/venues/search?ll=$ll&client_id=$client_id&client_secret=$client_secret&v=20130920&intent=browse&radius=100")
    val venues = future {
      val conn = urll.openConnection().asInstanceOf[HttpURLConnection]
      conn.getInputStream
    }

    val list = w[ListView]
    val progress = w[ProgressBar](null, android.R.attr.progressBarStyleLarge)
    // format: OFF
    async {
      val data = await(venues)
      val vs = (new ObjectMapper).readTree(data).get("response").get("venues").iterator.map { v ⇒
        val loc = v.get("location")
        (v.get("id").asText, v.get("name").asText, loc.get("lat").asDouble, loc.get("lng").asDouble)
      }
      await(progress ~@> Effects.fadeOut)
      Ui(list.setAdapter(new ArrayAdapter(activity, 0, vs.toArray) {
        override def getView(position: Int, itemView: View, parent: ViewGroup): View = {
          val item = getItem(position)
          val v = Option(itemView).getOrElse(w[TextView] ~> TextSize.large ~> padding(all = (3 sp)) ~> id(Id.text))
          findView[TextView](v, Id.text) ~> text(item._2)
          v ~> On.click {
            activity.addVenue(item._1, item._2, item._3, item._4)
            dismiss()
          }
        }
      }))
    }
    // format: ON

    new AlertDialog.Builder(activity) {
      setView(l[FrameLayout](list, progress))
      setNegativeButton(android.R.string.cancel, ())
    }.create()
  }
}

class NoteDialogFragment extends AddSomethingDialogFragment {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val input = new EditText(activity) {
      setHint(R.string.message_typenotehere)
      setMinLines(5)
      setGravity(Gravity.TOP)
    }
    new AlertDialog.Builder(activity) {
      setView(input)
      setPositiveButton(android.R.string.ok, Some(input.getText.toString).filter(_.length > 0).foreach(activity.addNote))
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
          activity.addVoice(mOutputPath)
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

class MeasurePulseDialogFragment extends AddSomethingDialogFragment with LayoutDsl with Tweaks with FragmentContext {
  val taps = Var(List[Long]())
  val beats = Rx {
    if (taps().length < 5) 0 else {
      val interval = taps().sliding(2).map { case List(a, b) ⇒ a - b }.sum.toInt / 4
      60000 / interval
    }
  }

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val layout = l[VerticalLinearLayout](
      w[TextView] ~> text(R.string.message_pulsehowto) ~> TextSize.medium,
      w[TextView] ~> beats.map(b ⇒ text(s"BPM: $b")),
      w[Button] ~> text("TAP") ~> On.click {
        taps.update(System.currentTimeMillis() :: taps().take(4))
      } ~> { x ⇒
        x.setHeight(100 dip)
        x.setGravity(Gravity.CENTER)
      }
    )

    new AlertDialog.Builder(ctx) {
      setView(layout)
      setPositiveButton(android.R.string.ok, Some(beats.now).filter(_ > 0).foreach(activity.addHeartbeat))
      setNegativeButton(android.R.string.cancel, ())
    }.create()
  }
}

