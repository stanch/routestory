package net.routestory.recording

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import android.app.Activity
import android.content.{ ComponentName, Context, Intent, ServiceConnection }
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.{ Bundle, IBinder }
import android.support.v4.app.Fragment
import android.support.v4.widget.SlidingPaneLayout
import android.view.{ Menu, MenuItem }
import android.widget._
import macroid.FullDsl._
import macroid.{ Tweak, IdGeneration, Ui }
import macroid.contrib.Layouts.VerticalLinearLayout
import net.routestory.R
import net.routestory.editing.EditActivity
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import net.routestory.util.Shortuuid
import akka.pattern.ask

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

trait RecordFragment { self: Fragment ⇒
  lazy val actorSystem = getActivity.asInstanceOf[RecordActivity].actorSystem.future
}

object RecordActivity {
  val requestCodeTakePhoto = 0
}

class RecordActivity extends RouteStoryActivity with IdGeneration with FragmentPaging {
  var progress = slot[ProgressBar]
  var slider = slot[SlidingPaneLayout]

  val actorSystem = Promise[ActorSystem]()
  val typewriter = actorSystem.future.map(_.actorSelection("/user/typewriter"))
  val cartographer = actorSystem.future.map(_.actorSelection("/user/cartographer"))
  implicit val timeout = Timeout(60000)

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(getUi {
      l[VerticalLinearLayout](
        activityProgress <~ wire(progress),
        l[SlidingPaneLayout](
          f[CartographyFragment].framed(Id.map, Tag.map),
          f[AddMediaFragment].framed(Id.addMedia, Tag.addMedia)
        ) <~ Tweak[SlidingPaneLayout] { x ⇒
            x.openPane()
            x.setSliderFadeColor(Color.argb(0, 0, 0, 0))
            x.setParallaxDistance(100 dp)
          } <~ wire(slider)
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
    runUi {
      progress <~~ waitProgress(actorSystem.future) <~~ waitProgress(firstLocation)
    }

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
        slider <~ Tweak[SlidingPaneLayout](_.closePane())
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

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == RecordActivity.requestCodeTakePhoto && resultCode == Activity.RESULT_OK) {
      runUi {
        f[AddPhotoCaption].pass("photoFile" → data.getStringExtra("photoFile")).factory
          .map(_.show(getSupportFragmentManager, Tag.caption))
      }
    }
  }
}
