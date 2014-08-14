package net.routestory.editing

import android.os.Bundle
import android.view.{ MenuItem, Menu }
import android.widget._
import macroid.FullDsl._
import macroid.{ IdGeneration, Ui, Tweak }
import macroid.akkafragments.AkkaActivity
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.viewable.Listable
import net.routestory.R
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }

import scala.concurrent.ExecutionContext.Implicits.global

class EditActivity extends RouteStoryActivity with IdGeneration with FragmentPaging with AkkaActivity {
  val actorSystemName = "StoryActorSystem"
  lazy val metadata = actorSystem.actorOf(MetadataActor.props, "metadata")

  lazy val storyId = getIntent.getStringExtra("id")
  lazy val story = app.hybridApi.story(storyId).go

  var progress = slot[ProgressBar]

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    setContentView(getUi(drawer(
      l[VerticalLinearLayout](
        activityProgress <~ wire(progress),
        getTabs(
          "Details" → f[MetadataFragment].factory
        )
      )
    )))
  }

  def save = Ui.nop

  def discard = Ui.nop

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
