package net.routestory.recording

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import android.app.Activity
import android.content.{ ComponentName, Context, Intent, ServiceConnection }
import android.media.MediaScannerConnection
import android.os.{ Bundle, IBinder }
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.view.{ Gravity, Menu, MenuItem }
import android.widget.{ EditText, TextView, RatingBar, ProgressBar }
import macroid.FullDsl._
import macroid.{ Tweak, IdGeneration, Ui }
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.contrib.{ TextTweaks, LpTweaks, PagerTweaks }
import net.routestory.R
import net.routestory.data.Story
import net.routestory.editing.EditActivity
import net.routestory.recording.manual.{ AddEasiness, AddPhotoCaption, AddMediaFragment }
import net.routestory.recording.suggest.SuggestionsFragment
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import net.routestory.util.Shortuuid
import akka.pattern.ask

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

trait RecordFragment { self: Fragment ⇒
  lazy val actorSystem = getActivity.asInstanceOf[RecordActivity].actorSystem.future
  def activity = getActivity.asInstanceOf[RecordActivity]
}

class RecordActivity extends RouteStoryActivity with IdGeneration with FragmentPaging {
  var progress = slot[ProgressBar]
  lazy val pager = this.find[ViewPager](Id.pager)

  val actorSystem = Promise[ActorSystem]()
  val typewriter = actorSystem.future.map(_.actorSelection("/user/typewriter"))
  val cartographer = actorSystem.future.map(_.actorSelection("/user/cartographer"))
  implicit val timeout = Timeout(60000)

  var lastPhotoFile: Option[File] = None
  val requestCodePhoto = 0

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    lastPhotoFile = for {
      sis ← Option(savedInstanceState)
      lpf ← Option(sis.getString("lastPhotoFile"))
    } yield new File(lpf)

    setContentView(getUi {
      l[VerticalLinearLayout](
        activityProgress <~ wire(progress),
        getTabs(
          "Add stuff" → f[AddMediaFragment].factory,
          "Map" → f[CartographyFragment].factory,
          "Suggestions" → f[SuggestionsFragment].factory
        )
      )
    })

    startService(new Intent(this, classOf[RecordService]))

    // setup action bar
    bar.setDisplayShowTitleEnabled(true)
    bar.setDisplayShowHomeEnabled(true)
  }

  object serviceConnection extends ServiceConnection {
    override def onServiceConnected(name: ComponentName, service: IBinder) =
      actorSystem.trySuccess(service.asInstanceOf[RecordService#RecordBinder].actorSystem)
    override def onServiceDisconnected(name: ComponentName) = ()
  }

  override def onStart() = {
    super.onStart()

    // wait for first location
    val firstLocation = cartographer.flatMap(_ ? Cartographer.QueryFirstLocation)

    // bind to the service and display progress
    bindService(new Intent(this, classOf[RecordService]), serviceConnection, Context.BIND_AUTO_CREATE)
    runUi(
      progress <~~ waitProgress(actorSystem.future) <~~ waitProgress(firstLocation),
      pager <~ PagerTweaks.page(1)
    )

    // if backup is available, suggest to restore
    typewriter.flatMap(_ ? Typewriter.QueryBackup).mapTo[Boolean].map {
      case false ⇒ // no big deal
      case true ⇒ runUi {
        dialog("Restore the story from a draft?") <~
          positiveYes(Ui(typewriter.foreach(_ ! Typewriter.RestoreBackup))) <~
          negativeNo(Ui(typewriter.foreach(_ ! Typewriter.DiscardBackup))) <~
          speak
      }
    }
  }

  override def onStop() = {
    super.onStop()
    unbindService(serviceConnection)
  }

  def discard = {
    val discarding = typewriter.flatMap(_ ? Typewriter.Discard)
    (progress <~~ waitProgress(discarding)) ~~ Ui {
      finish()
    }
  }

  def save = {
    val id = Shortuuid.make("story")
    val saving = typewriter.flatMap(_ ? Typewriter.Save(id))
    (progress <~~ waitProgress(saving)) ~~ Ui {
      val intent = new Intent(this, classOf[EditActivity])
      intent.putExtra("id", id)
      startActivity(intent)
      finish()
    }
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.addStuff ⇒
      runUi {
        pager <~ PagerTweaks.page(0, smoothScroll = true)
      }
      true
    case R.id.finish ⇒
      runUi {
        dialog("Do you want to finish and save the story?") <~
          positiveOk(f[AddEasiness].factory.map(_.show(getSupportFragmentManager, Tag.easiness))) <~
          negativeCancel(Ui.nop) <~
          speak
      }
      true
    case R.id.discard ⇒
      runUi {
        dialog("Do you want to discard the story?") <~
          positiveOk(discard) <~
          negativeCancel(Ui.nop) <~
          speak
      }
      true
    case _ ⇒ super.onOptionsItemSelected(item)
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.activity_record, menu)
    true
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    lastPhotoFile.foreach(f ⇒ outState.putString("lastPhotoFile", f.getAbsolutePath))
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == requestCodePhoto && resultCode == Activity.RESULT_OK) {
      lastPhotoFile foreach { file ⇒
        lastPhotoFile = None
        MediaScannerConnection.scanFile(getApplicationContext, Array(file.getAbsolutePath), null, null)
        runUi {
          f[AddPhotoCaption].pass("photoFile" → file.getAbsolutePath).factory
            .map(_.show(getSupportFragmentManager, Tag.caption))
        }
      }
    }
  }
}
