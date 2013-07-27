package net.routestory.recording

import android.support.v4.app.DialogFragment
import android.widget.EditText
import android.os.Bundle
import android.media.MediaRecorder
import MediaRecorder._
import android.app.AlertDialog
import java.io.File
import android.view.Gravity
import net.routestory.R
import android.view.View
import android.widget.Button
import android.app.Dialog
import org.scaloid.common._
import android.widget.TextView
import android.content.DialogInterface
import android.widget.LinearLayout
import net.routestory.parts.HapticImageButton
import android.content.Intent
import android.app.Activity
import net.routestory.parts.StoryFragment
import android.provider.MediaStore
import android.net.Uri
import akka.dataflow._
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.parts.Implicits._
import org.macroid.{ FragmentViewSearch, LayoutDsl }
import org.macroid.Transforms._
import org.macroid.Layouts
import net.routestory.parts.Transforms._

class AddMediaDialogFragment extends DialogFragment with StoryFragment {
  lazy val mPhotoPath = File.createTempFile("photo", ".jpg", getActivity.getExternalCacheDir).getAbsolutePath

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val activity = getActivity.asInstanceOf[RecordActivity]

    val view = activity.getLayoutInflater.inflate(R.layout.dialog_add_media, null)
    List(R.id.addTextNote, R.id.addVoiceNote, R.id.addHeartbeat) zip
      List(() ⇒ new NoteDialogFragment, () ⇒ new VoiceDialogFragment, () ⇒ new MeasurePulseDialogFragment) zip
      List(Tag.noteDialog, Tag.voiceDialog, Tag.photoDialog) map {
        case ((id, factory), tag) ⇒
          view.findViewById(id).asInstanceOf[HapticImageButton].setOnClickListener { v: View ⇒
            flow {
              await(activity.untrackAudio())
              switchToUiThread()
              dismiss()
              factory().show(activity.getSupportFragmentManager, tag)
            }
          }
      }

    view.findViewById(R.id.addPhoto).asInstanceOf[HapticImageButton].setOnClickListener { v: View ⇒
      flow {
        await(activity.untrackAudio())
        switchToUiThread()
        val intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(mPhotoPath)))
        startActivityForResult(intent, RecordActivity.REQUEST_CODE_TAKE_PICTURE)
      }
    }

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

class NoteDialogFragment extends DialogFragment {
  override def onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    getActivity.asInstanceOf[RecordActivity].trackAudio()
  }

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val activity = getActivity.asInstanceOf[RecordActivity]

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

class VoiceDialogFragment extends DialogFragment {
  var mMediaRecorder: MediaRecorder = null
  var mRecording = false
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

  override def onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    getActivity.asInstanceOf[RecordActivity].trackAudio()
  }

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val activity = getActivity.asInstanceOf[RecordActivity]

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
        activity.addVoice(mOutputPath)
      })
      setNegativeButton(R.string.button_cancel, { (d: DialogInterface, w: Int) ⇒
        if (mRecording) {
          stop()
        }
      })
    }.create()
  }
}

class MeasurePulseDialogFragment extends DialogFragment with FragmentViewSearch with LayoutDsl with Layouts {
  implicit lazy val ctx = getActivity
  var taps = List[Long]()
  def beats = if (taps.length < 5) 0 else {
    val interval = taps.sliding(2).map { case List(a, b) ⇒ a - b }.sum.toInt / 5
    60000 / interval
  }

  override def onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    getActivity.asInstanceOf[RecordActivity].trackAudio()
  }

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val activity = getActivity.asInstanceOf[RecordActivity]

    var bpm: TextView = null
    val layout = l[VerticalLinearLayout](
      w[TextView] ~> text(R.string.message_pulsehowto) ~> mediumText,
      w[TextView] ~> text("BPM: 0") ~> wire(bpm),
      w[Button] ~> { x ⇒
        x.setHeight(100 dip)
        x.setGravity(Gravity.CENTER)
        x.setText("TAP") // TODO: strings.xml
        x.setOnClickListener { v: View ⇒
          taps = (System.currentTimeMillis() :: taps) take (5)
          bpm.setText("BPM: %d".format(beats))
        }
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

