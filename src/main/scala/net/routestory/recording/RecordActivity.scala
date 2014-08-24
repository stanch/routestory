package net.routestory.recording

import akka.actor.ActorSystem
import android.content.{ ComponentName, Context, Intent, ServiceConnection }
import android.os.{ Bundle, IBinder }
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.view.{ Menu, MenuItem }
import android.widget.ProgressBar
import macroid.FullDsl._
import macroid.{ IdGeneration, Ui }
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.contrib.PagerTweaks
import net.routestory.R
import net.routestory.editing.EditActivity
import net.routestory.recording.manual.AddMediaFragment
import net.routestory.recording.suggest.SuggestionsFragment
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import net.routestory.util.Shortuuid

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

trait RecordFragment { self: Fragment ⇒
  lazy val actorSystem = getActivity.asInstanceOf[RecordActivity].actorSystem.future
}

class RecordActivity extends RouteStoryActivity with IdGeneration with FragmentPaging {
  var progress = slot[ProgressBar]
  lazy val pager = this.find[ViewPager](Id.pager)

  val actorSystem = Promise[ActorSystem]()
  val typewriter = actorSystem.future.map(_.actorSelection("/user/typewriter"))

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

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
    bindService(new Intent(this, classOf[RecordService]), serviceConnection, Context.BIND_AUTO_CREATE)
    runUi(
      progress <~ waitProgress(actorSystem.future),
      pager <~ PagerTweaks.page(1)
    )
  }

  override def onStop() = {
    super.onStop()
    unbindService(serviceConnection)
  }

  def discard = {
    val done = Promise[Unit]()
    typewriter.foreach(_ ! Typewriter.Discard(done))
    (progress <~~ waitProgress(done.future)) ~~ Ui {
      finish()
    }
  }

  def save = {
    val id = Shortuuid.make("story")
    val done = Promise[Unit]()
    typewriter.foreach(_ ! Typewriter.Save(id, done))
    (progress <~~ waitProgress(done.future)) ~~ Ui {
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
          positiveOk(save) <~
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
}
