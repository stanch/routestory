package net.routestory.recording

import akka.actor.{ ActorSystem, ActorRef }
import akka.pattern.ask
import akka.util.Timeout
import android.app.{ NotificationManager, PendingIntent }
import android.content.{ ComponentName, ServiceConnection, Context, Intent }
import android.location.{ Location ⇒ AndroidLocation }
import android.os.{ IBinder, Bundle }
import android.support.v4.app.{ Fragment, TaskStackBuilder, NotificationCompat }
import android.support.v4.view.ViewPager
import android.view.{ KeyEvent, Menu, MenuItem }
import android.widget.ProgressBar
import com.google.android.gms.common._
import com.google.android.gms.location.{ LocationClient, LocationListener, LocationRequest }
import macroid.FullDsl._
import macroid.akkafragments.AkkaActivity
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.Ui
import macroid.contrib.PagerTweaks
import macroid.{ IdGeneration, Tweak }
import net.routestory.R
import net.routestory.browsing.story.StoryActivity
import net.routestory.data.Story
import net.routestory.recording.logged.{ ControlPanelFragment, Dictaphone }
import net.routestory.recording.manual.AddMediaFragment
import net.routestory.recording.suggest.{ Suggester, SuggestionsFragment }
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import net.routestory.util.Implicits._
import net.routestory.util.Shortuuid
import play.api.data.mapping.{ From, To }
import play.api.libs.json.{ JsObject, JsValue, Json }
import resolvable.Resolvable

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Promise }
import scala.util.Try
import scala.util.control.NonFatal

trait RecordFragment { self: Fragment ⇒
  lazy val actorSystem = getActivity.asInstanceOf[RecordActivity].actorSystem.future
}

class RecordActivity extends RouteStoryActivity with IdGeneration with FragmentPaging {
  var progress = slot[ProgressBar]
  lazy val pager = this.find[ViewPager](Id.pager)

  val actorSystem = Promise[ActorSystem]()

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    setContentView(getUi {
      l[VerticalLinearLayout](
        activityProgress <~ wire(progress),
        getTabs(
          "Add stuff" → f[AddMediaFragment].factory,
          "Map" → f[CartographyFragment].factory,
          "Suggestions" → f[SuggestionsFragment].factory,
          "Control panel" → f[ControlPanelFragment].factory
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

  def createNew = {
    progress <~~ waitProgress(async {
      //      implicit val timeout = Timeout(5 seconds)
      //      val id = Shortuuid.make("story")
      //      val chapter = await((typewriter ? Typewriter.Backup).mapTo[Story.Chapter])
      //      val story = Story(id, Story.Meta(None, None), List(chapter), None)
      //      // TODO: app.createStory(story)
      //      val intent = new Intent(this, classOf[StoryActivity])
      //      intent.putExtra("id", id)
      //      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      //      Ui(startActivity(intent)).run
    } recover {
      case NonFatal(t) ⇒ t.printStackTrace(); throw t
    })
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.addStuff ⇒
      runUi {
        pager <~ PagerTweaks.page(0, smoothScroll = true)
      }
      true
    case R.id.controlPanel ⇒
      runUi {
        pager <~ PagerTweaks.page(3, smoothScroll = true)
      }
      true
    case R.id.stopRecord ⇒
      runUi {
        dialog(getResources.getString(R.string.message_stoprecord)) <~
          positiveYes(createNew) <~
          negativeNo(Ui.nop) <~
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
