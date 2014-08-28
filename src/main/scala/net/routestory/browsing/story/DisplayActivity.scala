package net.routestory.browsing.story

import java.io.{ File, FileInputStream }

import akka.actor._
import akka.pattern.pipe
import android.content.Intent
import android.net.Uri
import android.nfc.{ NdefMessage, NfcAdapter }
import android.os.Bundle
import android.util.Log
import android.view.{ Menu, MenuItem }
import android.widget.{ FrameLayout, ProgressBar }
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api._
import com.google.android.gms.drive.DriveApi.ContentsResult
import com.google.android.gms.drive.DriveFolder.DriveFileResult
import com.google.android.gms.drive.{ DriveFile, Drive, MetadataChangeSet }
import macroid.FullDsl._
import macroid.akkafragments.AkkaActivity
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.{ AppContext, IdGeneration, Ui }
import net.routestory.R
import net.routestory.data.{ Clustering, Story, Timed }
import net.routestory.editing.EditActivity
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import net.routestory.util.PlayServicesResolution
import org.apache.commons.io.IOUtils

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Future, Promise }

object DisplayActivity {
  object NfcIntent {
    def unapply(i: Intent) = Option(i).filter(_.getAction == NfcAdapter.ACTION_NDEF_DISCOVERED).flatMap { intent ⇒
      Option(intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) map { rawMsgs ⇒
        val msg = rawMsgs(0).asInstanceOf[NdefMessage]
        val rec = msg.getRecords()(0)
        Uri.parse(new String(rec.getPayload))
      }
    }
  }
  object ViewIntent {
    def unapply(i: Intent) = Option(i).filter(_.getAction == Intent.ACTION_VIEW).map(_.getData)
  }
  object PlainIntent {
    def unapply(i: Intent) = Option(i).filter(_.hasExtra("id")).map(_.getStringExtra("id"))
  }
}

class DisplayActivity extends RouteStoryActivity with AkkaActivity with FragmentPaging with IdGeneration {
  import net.routestory.browsing.story.DisplayActivity._

  val actorSystemName = "StoryActorSystem"
  lazy val coordinator = actorSystem.actorOf(Coordinator.props(this), "coordinator")
  lazy val timeliner = actorSystem.actorOf(Timeliner.props, "timeliner")
  lazy val diver = actorSystem.actorOf(Diver.props, "diver")
  lazy val previewer = actorSystem.actorOf(Previewer.props, "previewer")
  lazy val astronaut = actorSystem.actorOf(Astronaut.props, "astronaut")

  lazy val storyId = getIntent match {
    case NfcIntent(uri) ⇒
      "story-" + uri.getLastPathSegment
    case ViewIntent(uri) ⇒
      "story-" + uri.getLastPathSegment
    case PlainIntent(i) ⇒
      i
    case _ ⇒
      finish(); ""
  }

  lazy val story = app.hybridApi.story(storyId).go

  var progress = slot[ProgressBar]
  var view = slot[FrameLayout]

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    story // start loading
    (coordinator, timeliner, diver, previewer, astronaut) // start actors

    setContentView(getUi(drawer(
      l[VerticalLinearLayout](
        activityProgress <~ wire(progress),
        getTabs(
          "Dive" → f[DiveFragment].factory,
          //"Details" → f[StoryDetailsFragment].factory,
          "Space" → f[SpaceFragment].factory,
          "Timeline" → f[TimelineFragment].factory
        )
      )
    )))

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    runUi(progress <~ waitProgress(story))

    story mapUi { s ⇒
      bar.setTitle(s.meta.title.filter(!_.isEmpty).getOrElse(getResources.getString(R.string.untitled)))
      s.author.map(_.name).foreach(name ⇒ bar.setSubtitle("by " + name))
      coordinator ! Coordinator.UpdateChapter(s.chapters(0))
    } onFailureUi {
      case t ⇒
        t.printStackTrace()
        runUi(toast("Failed to load the story") <~ fry)
        finish()
    }
  }

  override def onDestroy() = {
    super.onDestroy()
    actorSystem.shutdown()
  }

  def connectToDrive = {
    val connected = Promise[GoogleApiClient]()

    object connectionCallbacks extends GoogleApiClient.ConnectionCallbacks {
      override def onConnected(p1: Bundle) = connected.trySuccess(apiClient)
      override def onConnectionSuspended(p1: Int) = ()
    }

    object connectionFailedListener extends GoogleApiClient.OnConnectionFailedListener {
      override def onConnectionFailed(connectionResult: ConnectionResult) =
        PlayServicesResolution.resolve(connectionResult)
    }

    lazy val apiClient = new GoogleApiClient.Builder(this, connectionCallbacks, connectionFailedListener)
      .addApi(Drive.API)
      .addScope(Drive.SCOPE_FILE)
      .build()

    apiClient.connect()
    connected.future
  }

  implicit class RichPendingResult[A <: Result](result: PendingResult[A]) {
    def future[B](getter: A ⇒ B) = {
      val promise = Promise[B]()
      result.setResultCallback(new ResultCallback[A] {
        override def onResult(result: A) = if (result.getStatus.isSuccess) {
          promise.success(getter(result))
        } else {
          promise.failure(new Exception(result.getStatus.getStatusMessage))
        }
      })
      promise.future
    }
  }

  def writeToDrive(client: GoogleApiClient, name: String, file: File) = async {
    val newContents = await {
      Drive.DriveApi.newContents(client)
        .future(_.getContents)
    }

    val changeSet = new MetadataChangeSet.Builder()
      .setTitle(name + ".zip")
      .setMimeType("application/routestory")
      .build()

    val driveFile = await {
      Drive.DriveApi.getRootFolder(client)
        .createFile(client, changeSet, newContents)
        .future(_.getDriveFile)
    }

    val contents = await {
      driveFile.openContents(client, DriveFile.MODE_WRITE_ONLY, null)
        .future(_.getContents)
    }

    val stream = new FileInputStream(file)
    IOUtils.copy(stream, contents.getOutputStream)

    val status = await {
      driveFile.commitAndCloseContents(client, contents)
        .future(identity)
    }
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.edit ⇒
      val intent = new Intent(this, classOf[EditActivity])
      intent.putExtra("id", storyId)
      startActivity(intent)
      finish()
      true
    case R.id.delete ⇒
      runUi {
        dialog("Do you want to delete this story?") <~
          positiveOk(Ui(app.hybridApi.deleteStory(storyId)) ~ Ui(finish())) <~
          negativeCancel(Ui.nop) <~
          speak
      }
      true
    case R.id.share ⇒
      val writing = async {
        val drive = await(connectToDrive)
        val s = await(story)
        val file = File.createTempFile("story", ".zip")
        await(net.routestory.zip.Save(s, file))
        await(writeToDrive(drive, s.meta.title.getOrElse("Untitled story"), file))
      } recover {
        case t ⇒ t.printStackTrace()
      }
      runUi {
        progress <~ waitProgress(writing)
      }
      true
    case _ ⇒ super.onOptionsItemSelected(item)
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.activity_display, menu)
    val editable = app.hybridApi.isLocal(storyId)
    menu.findItem(R.id.edit).setVisible(editable)
    menu.findItem(R.id.delete).setVisible(editable)
    true
  }
}

object Coordinator {
  case class UpdateChapter(chapter: Story.Chapter)
  case class UpdateTree(chapter: Story.Chapter, tree: Option[Clustering.Tree[Unit]])
  case class UpdateFocus(chapter: Story.Chapter, focus: Int)
  case object Remind

  def props(activity: DisplayActivity)(implicit appCtx: AppContext) = Props(new Coordinator(activity))
}

class Coordinator(activity: DisplayActivity)(implicit appCtx: AppContext) extends Actor with ActorLogging {
  import Coordinator._

  var chapter: Option[Story.Chapter] = None
  var tree: Option[Clustering.Tree[Unit]] = None
  var focus = 0

  lazy val timeliner = context.actorSelection("../timeliner")
  lazy val diver = context.actorSelection("../diver")
  lazy val previewer = context.actorSelection("../previewer")
  lazy val astronaut = context.actorSelection("../astronaut")
  lazy val recipients = List(timeliner, diver, previewer, astronaut)

  def receive = {
    case m @ UpdateChapter(c) ⇒
      log.debug("Chapter loaded")
      chapter = Some(c)

      val media = c.knownElements.toList flatMap {
        case Timed(_, m: Story.MediaElement) ⇒ m.data :: Nil
        case _ ⇒ Nil
      }
      val clustering = Future(Clustering.cluster(c))
        .map(t ⇒ UpdateTree(c, t))
        .pipeTo(self)

      runUi {
        activity.progress <~~ waitProgress(clustering :: media)
      }
      recipients.foreach(_ ! m)

    case m @ UpdateTree(c, t) ⇒
      tree = t
      recipients.foreach(_ ! m)

    case m @ UpdateFocus(c, f) ⇒
      focus = f
      recipients.foreach(_ ! m)

    case Remind ⇒
      chapter foreach { c ⇒
        sender ! UpdateChapter(c)
        sender ! UpdateTree(c, tree)
        sender ! UpdateFocus(c, focus)
      }

    case _ ⇒
  }
}
