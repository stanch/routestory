package net.routestory.util

import java.io.File

import android.app.Activity
import android.content.{ Intent, DialogInterface }
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.{ Environment, Bundle }
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.widget.EditText
import macroid.FullDsl._
import macroid.{ Tweak, Ui }
import net.routestory.data.Story
import net.routestory.recording.Typewriter
import net.routestory.recording.manual.AdderDialog
import net.routestory.ui.RouteStoryActivity
import scala.concurrent.ExecutionContext.Implicits.global

class TakePhotoActivity extends RouteStoryActivity {
  def newPhotoFile = {
    val root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RouteStory")
    root.mkdirs()
    File.createTempFile("photo", ".jpg", root)
  }

  var photoFile: Option[File] = None

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    photoFile = for {
      sis ← Option(savedInstanceState)
      lpf ← Option(sis.getString("photoFile"))
    } yield new File(lpf)
  }

  override def onSaveInstanceState(outState: Bundle) = {
    photoFile.foreach(f ⇒ outState.putString("photoFile", f.getAbsolutePath))
    super.onSaveInstanceState(outState)
  }

  override def onStart() = {
    super.onStart()
    if (photoFile.isEmpty) {
      photoFile = Some(newPhotoFile)
      val intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
      intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile.get))
      startActivityForResult(intent, 0)
    }
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == 0 && resultCode == Activity.RESULT_OK && photoFile.isDefined) {
      MediaScannerConnection.scanFile(getApplicationContext, Array(photoFile.get.getAbsolutePath), null, null)
      setResult(Activity.RESULT_OK, new Intent().putExtra("photoFile", photoFile.get.getAbsolutePath))
      finish()
    } else {
      setResult(Activity.RESULT_CANCELED)
      finish()
    }
  }
}
