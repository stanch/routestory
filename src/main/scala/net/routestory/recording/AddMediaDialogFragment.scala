package net.routestory.recording

import android.support.v4.app.DialogFragment
import android.widget._
import android.os.Bundle
import android.media.MediaRecorder
import MediaRecorder._
import android.app.AlertDialog
import java.io.File
import android.view.{ Gravity, View }
import net.routestory.R
import android.app.Dialog
import org.scaloid.common._
import android.content.DialogInterface
import net.routestory.parts.HapticImageButton
import android.content.Intent
import android.app.Activity
import net.routestory.parts.StoryFragment
import android.provider.MediaStore
import android.net.Uri
import akka.dataflow._
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.parts.Implicits._
import org.macroid._
import net.routestory.parts.Tweaks._
import java.net.{ HttpURLConnection, URLEncoder, URL }
import org.apache.commons.io.IOUtils
import scala.concurrent.future
import org.macroid.Layouts.{ GravityGridLayout, VerticalLinearLayout }
import org.macroid.Util.Thunk

class AddMediaDialogFragment extends DialogFragment with StoryFragment {
  lazy val mPhotoPath = File.createTempFile("photo", ".jpg", getActivity.getExternalCacheDir).getAbsolutePath

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val activity = getActivity.asInstanceOf[RecordActivity]

    def clicker(factory: Thunk[DialogFragment], tag: String) = flow {
      await(activity.untrackAudio())
      switchToUiThread()
      dismiss()
      factory().show(activity.getSupportFragmentManager, tag)
    }

    def cameraClicker = flow {
      await(activity.untrackAudio())
      switchToUiThread()
      val intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
      intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(mPhotoPath)))
      startActivityForResult(intent, RecordActivity.REQUEST_CODE_TAKE_PICTURE)
    }

    // format: OFF
    val buttons = Seq(
      (R.drawable.take_a_picture, Thunk(cameraClicker)),
      (R.drawable.leave_a_note, Thunk(clicker(ff[NoteDialogFragment](), Tag.noteDialog))),
      (R.drawable.record_sound, Thunk(clicker(ff[VoiceDialogFragment](), Tag.noteDialog))),
      (R.drawable.record_heartbeat, Thunk(clicker(ff[MeasurePulseDialogFragment](), Tag.noteDialog))),
      (R.drawable.foursquare_logo, Thunk(clicker(ff[FoursquareDialogFragment](), Tag.fsqDialog)))
    ) map { case (b, c) ⇒
      w[HapticImageButton] ~> bg(b) ~> ThunkOn.click(c) ~> layoutParamsOf[GravityGridLayout](Gravity.CENTER)
    }
    // format: ON

    val view = l[ScrollView](
      l[GravityGridLayout]() ~> addViews(buttons) ~> { x ⇒
        x.setOrientation(GridLayout.HORIZONTAL)
        x.setColumnCount(2)
        x.setRowCount(3)
      }
    )
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

class AddSomethingDialogFragment extends DialogFragment {
  lazy val activity = getActivity.asInstanceOf[RecordActivity]

  override def onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    activity.trackAudio()
  }
}

class FoursquareDialogFragment extends AddSomethingDialogFragment with Concurrency {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val client_id = "0TORHPL0MPUG24YGBVNINGV2LREZJCD0XBCDCBMFC0JPDO05"
    val client_secret = "SIPSHLBOLADA2HW3RT44GE14OGBDNSM0VPBN4MSEWH2E4VLN"
    val ll = "40.7,-74"
    val url = new URL(s"https://api.foursquare.com/v2/venues/search?ll=$ll&client_id=$client_id&client_secret=$client_secret")
    val venues = future {
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      IOUtils.toString(conn.getInputStream)
    }

    val view = new TextView(activity)
    venues.onSuccessUi { case v ⇒ view.setText(v) }

    new AlertDialog.Builder(activity) {
      setView(view)
      setPositiveButton(R.string.button_save, { (d: DialogInterface, w: Int) ⇒
        ;
      })
      setNegativeButton(R.string.button_cancel, { (d: DialogInterface, w: Int) ⇒ ; })
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
      setPositiveButton(R.string.button_save, { (d: DialogInterface, w: Int) ⇒
        val note = input.getText.toString
        if (note.length() > 0) {
          activity.addNote(note)
        }
      })
      setNegativeButton(R.string.button_cancel, { (d: DialogInterface, w: Int) ⇒ ; })
    }.create()
  }
}

class VoiceDialogFragment extends AddSomethingDialogFragment {
  var mMediaRecorder: MediaRecorder = null
  var mRecording = false
  var mRecorded = false
  var mStartStop: Button = null
  lazy val mOutputPath = File.createTempFile("voice", ".mp4", getActivity.getExternalCacheDir).getAbsolutePath

  def start() {
    mMediaRecorder = new MediaRecorder() {
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
      mStartStop.setText("Stop recording")
    } catch {
      case e: Throwable ⇒ e.printStackTrace()
    }
  }

  def stop() {
    mMediaRecorder.stop()
    mMediaRecorder.release()
    mMediaRecorder = null
    mRecording = false
    mStartStop.setText("Click if you want to try again")
  }

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    mStartStop = new Button(activity) {
      setText("Start recording")
      setOnClickListener { v: View ⇒
        if (!mRecording) {
          start()
        } else {
          stop()
        }
      }
    }

    new AlertDialog.Builder(activity) {
      setView(mStartStop)
      setPositiveButton(R.string.button_save, { (d: DialogInterface, w: Int) ⇒
        if (mRecording) {
          stop()
        }
        if (mRecorded) {
          activity.addVoice(mOutputPath)
        }
      })
      setNegativeButton(R.string.button_cancel, { (d: DialogInterface, w: Int) ⇒
        if (mRecording) {
          stop()
        }
      })
    }.create()
  }
}

class MeasurePulseDialogFragment extends AddSomethingDialogFragment with LayoutDsl with Tweaks with FragmentContext {
  var taps = List[Long]()
  def beats = if (taps.length < 5) 0 else {
    val interval = taps.sliding(2).map { case List(a, b) ⇒ a - b }.sum.toInt / 4
    60000 / interval
  }

  var bpm: TextView = _

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val layout = l[VerticalLinearLayout](
      w[TextView] ~> text(R.string.message_pulsehowto) ~> mediumText,
      w[TextView] ~> text("BPM: 0") ~> wire(bpm),
      w[Button] ~> text("TAP") ~> On.click {
        taps = System.currentTimeMillis() :: taps.take(4)
        bpm ~> text(s"BPM: $beats")
      } ~> { x ⇒
        x.setHeight(100 dip)
        x.setGravity(Gravity.CENTER)
      }
    )

    new AlertDialog.Builder(ctx) {
      setView(layout)
      setPositiveButton(R.string.button_save, { (d: DialogInterface, w: Int) ⇒
        if (beats > 0) {
          activity.addHeartbeat(beats)
        }
      })
      setNegativeButton(R.string.button_cancel, { (d: DialogInterface, w: Int) ⇒ ; })
    }.create()
  }
}

