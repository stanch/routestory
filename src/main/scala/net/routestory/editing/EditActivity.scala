package net.routestory.editing

import akka.actor.{ Actor, Props }
import android.os.Bundle
import android.view.{ Menu, MenuItem }
import android.widget._
import macroid.FullDsl._
import macroid.akkafragments.AkkaActivity
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.{ AppContext, IdGeneration, Ui }
import net.routestory.{ Apis, R }
import net.routestory.data.Story
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

class EditActivity extends RouteStoryActivity with IdGeneration with FragmentPaging with AkkaActivity {
  val actorSystemName = "StoryActorSystem"
  lazy val editor = actorSystem.actorOf(Editor.props(app), "editor")
  lazy val metadata = actorSystem.actorOf(Metadata.props, "metadata")

  lazy val storyId = getIntent.getStringExtra("id")
  lazy val story = app.hybridApi.story(storyId).go

  var progress = slot[ProgressBar]

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    (editor, metadata) // init actors

    setContentView(getUi(drawer(
      l[VerticalLinearLayout](
        activityProgress <~ wire(progress) <~ waitProgress(story),
        getTabs(
          "Details" → f[MetadataFragment].factory
        )
      )
    )))

    story foreach { s ⇒
      editor ! Editor.Init(s)
    }
  }

  def save = {
    val done = Promise[Unit]()
    editor ! Editor.Save(done)
    (progress <~~ waitProgress(done.future)) ~~ Ui {
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
  case class Save(done: Promise[Unit])
  case object Remind
}

class Editor(apis: Apis)(implicit ctx: AppContext) extends Actor {
  import net.routestory.editing.Editor._

  lazy val metadata = context.actorSelection("../metadata")

  var story: Story = Story.empty("")

  def receive = {
    case Init(s) ⇒
      story = s
      metadata ! Init(story)

    case Remind ⇒
      sender ! Init(story)

    case Meta(m) ⇒
      story = story.withMeta(m)

    case Save(done) ⇒
      apis.hybridApi.updateStory(story)
      done.success(())
  }
}
