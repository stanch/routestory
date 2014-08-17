package net.routestory.editing

import akka.actor.{ TypedActor, Props, Actor }
import android.os.Bundle
import android.view.{ MenuItem, Menu }
import android.widget._
import macroid.FullDsl._
import macroid.{ AppContext, IdGeneration, Ui, Tweak }
import macroid.akkafragments.AkkaActivity
import macroid.contrib.Layouts.VerticalLinearLayout
import net.routestory.R
import net.routestory.data.Story
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }

import scala.concurrent.ExecutionContext.Implicits.global

class EditActivity extends RouteStoryActivity with IdGeneration with FragmentPaging with AkkaActivity {
  val actorSystemName = "StoryActorSystem"
  lazy val editor = actorSystem.actorOf(Editor.props, "editor")
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

  def save = Ui.nop

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
  def props(implicit ctx: AppContext) = Props(new Editor)

  case class Init(story: Story)
  case class Meta(meta: Story.Meta)
}

class Editor(implicit ctx: AppContext) extends Actor {
  import Editor._

  lazy val metadata = context.actorSelection("../metadata")

  var story: Story = Story.empty("")

  def receive = {
    case Init(s) ⇒
      story = s
      metadata ! Metadata.Init(s.meta)

    case Meta(m) ⇒
      story = story.withMeta(m)
      runUi(toast(s"got $m") <~ fry)
  }
}
