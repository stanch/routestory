package net.routestory.editing

import akka.actor.{ Actor, Props }
import android.content.Intent
import android.os.Bundle
import android.view.{ Menu, MenuItem }
import android.widget._
import macroid.FullDsl._
import macroid.akkafragments.AkkaActivity
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.{ AppContext, IdGeneration, Ui }
import net.routestory.browsing.story.DisplayActivity
import net.routestory.{ Apis, R }
import net.routestory.data.{ Timed, Story }
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

class EditActivity extends RouteStoryActivity with IdGeneration with FragmentPaging with AkkaActivity {
  val actorSystemName = "StoryActorSystem"
  lazy val editor = actorSystem.actorOf(Editor.props(app), "editor")
  lazy val metadata = actorSystem.actorOf(Metadata.props, "metadata")
  lazy val elemental = actorSystem.actorOf(Elemental.props, "elemental")

  lazy val storyId = getIntent.getStringExtra("id")
  lazy val story = app.hybridApi.story(storyId).go

  var progress = slot[ProgressBar]

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    (editor, metadata, elemental) // init actors

    setContentView(getUi(
      l[VerticalLinearLayout](
        activityProgress <~ wire(progress) <~ waitProgress(story),
        getTabs(
          "Details" → f[MetadataFragment].factory,
          "Elements" → f[ElementsFragment].factory
        )
      )
    ))

    story foreach { s ⇒
      editor ! Editor.Init(s)
    }
  }

  def save = {
    val done = Promise[Unit]()
    editor ! Editor.Save(done)
    (progress <~~ waitProgress(done.future)) ~~ Ui {
      val intent = new Intent(this, classOf[DisplayActivity])
      intent.putExtra("id", storyId)
      startActivity(intent)
      finish()
    }
  }

  def discard = Ui {
    finish()
  }

  override def onDestroy() = {
    super.onDestroy()
    actorSystem.shutdown()
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.finish ⇒
      save.run
      true
    case R.id.discard ⇒
      discard.run
      true
    case android.R.id.home ⇒
      discard.run
      true
    case _ ⇒ super.onOptionsItemSelected(item)
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.activity_edit, menu)
    true
  }
}

object Editor {
  def props(apis: Apis)(implicit ctx: AppContext) = Props(new Editor(apis))

  case class Init(story: Story)
  case class Meta(meta: Story.Meta)
  // TODO: this should be index-based instead!
  case class RemoveElement(element: Timed[Story.KnownElement])
  case class Save(done: Promise[Unit])
  case object Remind
}

class Editor(apis: Apis)(implicit ctx: AppContext) extends Actor {
  import net.routestory.editing.Editor._

  lazy val metadata = context.actorSelection("../metadata")
  lazy val elemental = context.actorSelection("../elemental")

  var story: Option[Story] = None

  def receive = {
    case Init(s) ⇒
      story = Some(s)
      metadata ! Init(s)
      elemental ! Init(s)

    case Remind ⇒
      story.foreach(s ⇒ sender ! Init(s))

    case Meta(m) ⇒
      story = story.map(_.withMeta(m))

    case RemoveElement(element) ⇒
      story = story map { s ⇒
        val chapter = s.chapters.head
        val without = chapter.copy(elements = chapter.elements diff Vector(element))
        s.copy(chapters = List(without))
      }
      story.foreach(s ⇒ elemental ! RemoveElement(element))

    case Save(done) ⇒
      story.foreach(s ⇒ apis.hybridApi.updateStory(s))
      done.success(())
  }
}
