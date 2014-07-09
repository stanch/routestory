package net.routestory.browsing

import akka.actor._
import android.content.Intent
import android.net.Uri
import android.nfc.{ NdefMessage, NfcAdapter }
import android.os.Bundle
import android.view.ViewGroup.LayoutParams._
import android.widget.{ LinearLayout, ProgressBar }
import macroid.FullDsl._
import macroid.IdGeneration
import macroid.akkafragments.AkkaActivity
import net.routestory.R
import net.routestory.data.Story
import net.routestory.data.Clustering
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import akka.pattern.pipe

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object StoryActivity {
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

class StoryActivity extends RouteStoryActivity with AkkaActivity with FragmentPaging with IdGeneration {
  import net.routestory.browsing.StoryActivity._

  val actorSystemName = "StoryActorSystem"
  lazy val coordinator = actorSystem.actorOf(Coordinator.props(this), "coordinator")
  lazy val timeliner = actorSystem.actorOf(Timeliner.props, "timeliner")
  //lazy val viewer = actorSystem.actorOf(Viewer.props, "viewer")
  val viewer = new Viewer2
  lazy val diver = actorSystem.actorOf(Diver.props, "diver")
  lazy val astronaut = actorSystem.actorOf(Astronaut.props, "astronaut")

  private lazy val id = getIntent match {
    case NfcIntent(uri) ⇒
      "story-" + uri.getLastPathSegment
    case ViewIntent(uri) ⇒
      "story-" + uri.getLastPathSegment
    case PlainIntent(i) ⇒
      i
    case _ ⇒
      finish(); ""
  }

  lazy val story = app.hybridApi.story(id).go
  lazy val media = story map { s ⇒
    s.chapters(0).elements flatMap {
      case m: Story.MediaElement ⇒ m.data :: Nil
      case _ ⇒ Nil
    }
  }

  var progress = slot[ProgressBar]
  lazy val nfcAdapter: Option[NfcAdapter] = Option(NfcAdapter.getDefaultAdapter(getApplicationContext))

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    media // start loading
    (coordinator, timeliner, viewer, diver, astronaut) // start actors

    setContentView(getUi(drawer(
      l[LinearLayout](
        activityProgress <~ wire(progress),
        //getTabs(
        //"Dive" → f[DiveFragment].factory,
        //"Details" → f[StoryDetailsFragment].factory,
        //"Space" → f[SpaceFragment].factory
        //) <~
        //  lp[LinearLayout](MATCH_PARENT, 0, 2.0f),

        f[StoryElementFragment].framed(Id.preview, Tag.preview) // <~
      //lp[LinearLayout](MATCH_PARENT, 0, 1.0f)
      )
    )))

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    runUi {
      progress <~~ waitProgress(story) <~~ media.map(waitProgress)
    }

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
}

class Coor

object Coordinator {
  case class UpdateChapter(chapter: Story.Chapter)
  case class UpdateTree(chapter: Story.Chapter, tree: Option[Clustering.Tree])
  case class UpdateCue(chapter: Story.Chapter, cue: Int)
  case object Remind

  def props(activity: StoryActivity) = Props(new Coordinator(activity))
}

class Coordinator(activity: StoryActivity) extends Actor with ActorLogging {
  import net.routestory.browsing.Coordinator._

  var chapter: Option[Story.Chapter] = None
  var tree: Option[Clustering.Tree] = None

  lazy val timeliner = context.actorSelection("../timeliner")
  lazy val viewer = context.actorSelection("../viewer")
  //lazy val diver = context.actorSelection("../diver")
  //lazy val astronaut = context.actorSelection("../astronaut")
  lazy val recipients = List(timeliner, viewer)

  def receive = {
    case m @ UpdateChapter(c) ⇒
      log.debug("Chapter loaded")
      chapter = Some(c)

      val media = c.elements flatMap {
        case m: Story.MediaElement ⇒ m.data :: Nil
        case _ ⇒ Nil
      }
      val clustering = Future(Clustering.cluster(c))
        .map(t ⇒ UpdateTree(c, t))
        .pipeTo(self)

      runUi {
        activity.progress <~~ waitProgress(media) <~ waitProgress(clustering)
      }
      recipients.foreach(_ ! m)

    case m @ UpdateTree(c, t) ⇒
      log.debug("Clustering finished")
      tree = t
      recipients.foreach(_ ! m)

    case Remind ⇒
      chapter foreach { c ⇒
        sender ! UpdateChapter(c)
        sender ! UpdateTree(c, tree)
      }
  }
}
