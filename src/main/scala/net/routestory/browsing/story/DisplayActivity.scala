package net.routestory.browsing.story

import akka.actor._
import akka.pattern.pipe
import android.content.Intent
import android.net.Uri
import android.nfc.{ NdefMessage, NfcAdapter }
import android.os.Bundle
import android.view.ViewGroup.LayoutParams._
import android.view.{ MenuItem, Menu, LayoutInflater, ViewGroup }
import android.widget.{ FrameLayout, LinearLayout, ProgressBar }
import macroid.FullDsl._
import macroid.akkafragments.AkkaActivity
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.Ui
import macroid.contrib.PagerTweaks
import macroid.{ AppContext, IdGeneration }
import net.routestory.R
import net.routestory.browsing._
import net.routestory.data.{ Timed, Clustering, Story }
import net.routestory.editing.EditActivity
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity, RouteStoryFragment }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
  import DisplayActivity._

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
      bar.setSubtitle("by " + s.author.map(_.name).getOrElse("me"))
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

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.edit ⇒
      val intent = new Intent(this, classOf[EditActivity])
      intent.putExtra("id", storyId)
      startActivity(intent)
      true
    case R.id.delete ⇒
      runUi {
        dialog("Do you want to delete this story?") <~
          positiveOk(Ui(app.hybridApi.deleteStory(storyId)) ~ Ui(finish())) <~
          negativeCancel(Ui.nop) <~
          speak
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
