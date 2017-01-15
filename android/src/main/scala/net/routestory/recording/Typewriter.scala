package net.routestory.recording

import java.io.{ FileOutputStream, File }

import akka.actor.{ ActorLogging, Actor, Props }
import akka.pattern.pipe
import android.app.AlertDialog
import com.javadocmd.simplelatlng.LatLng
import macroid.{ Ui, AppContext }
import macroid.FullDsl._
import net.routestory.{ RouteStoryApp, Apis }
import net.routestory.data.{ Clustering, Story, Timed }
import org.apache.commons.io.IOUtils
import play.api.data.mapping.To
import play.api.libs.json.JsObject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Typewriter {
  case class Element(element: Story.KnownElement)
  case class Location(location: LatLng)
  case class Easiness(easiness: Float)
  case class Suggestions(number: Int)
  case object Remind

  case object Discard
  case class Save(storyId: String)

  case class Clustered(tree: Option[Clustering.Tree[Unit]])

  case object QueryBackup
  case object Backup
  case object RestoreBackup
  case object DiscardBackup
  case class Restored(chapter: Story.Chapter)

  def props(service: RecordService, app: RouteStoryApp)(implicit ctx: AppContext) = Props(new Typewriter(service, app))
}

/** An actor that maintains the chapter being recorded */
class Typewriter(service: RecordService, app: RouteStoryApp)(implicit ctx: AppContext) extends Actor with ActorLogging {
  import Typewriter._

  def cartographer = context.actorSelection("../cartographer")
  var chapter = Story.Chapter.empty
  var tree: Option[Clustering.Tree[Unit]] = None

  var studyInfo = Story.StudyInfo.empty

  val backupFile = new File(ctx.get.getFilesDir, "chapterBackup.json")
  var restoreSuggested = false
  var saved = false

  def elementName(element: Story.KnownElement) = element match {
    case x: Story.Photo ⇒ "a photo"
    case x: Story.VoiceNote ⇒ "a voice note"
    case x: Story.TextNote ⇒ "a text note"
    case x: Story.Sound ⇒ "ambient sound"
    case x: Story.FlickrPhoto ⇒ "a photo from Flickr"
    case x: Story.InstagramPhoto ⇒ "a photo from Instagram"
    case x: Story.FoursquareVenue ⇒ "a Foursquare venue"
  }

  def cluster() = Future {
    tree.map(t ⇒ Clustering.appendLast(t, chapter))
      .orElse(Clustering.cluster(chapter))
  }.map(Clustered).pipeTo(self)

  def receive = {
    case Element(element) ⇒
      chapter = chapter.withElement(Timed(chapter.ts, element))
      cluster()
      runUi {
        toast(s"Added ${elementName(element)}") <~ fry
      }
      self ! Backup

    case Location(location) ⇒
      chapter = chapter.withLocation(Timed(chapter.ts, location))
      if (chapter.locations.length == 1) cluster()
      if (chapter.locations.length % 10 == 1) self ! Backup
      cartographer ! Cartographer.UpdateRoute(chapter)

    case Easiness(easiness) ⇒
      studyInfo = studyInfo.withEasiness(easiness)

    case Suggestions(number) ⇒
      studyInfo = studyInfo.addSuggestions(chapter.ts, number)

    case Restored(c) ⇒
      chapter = c
      tree = None
      cartographer ! Cartographer.UpdateRoute(chapter)
      cluster()
      runUi {
        toast("Restored from draft")
      }

    case Clustered(t) ⇒
      tree = t
      cartographer ! Cartographer.UpdateMarkers(chapter, tree)

    case Remind ⇒
      cartographer ! Cartographer.UpdateRoute(chapter)
      cartographer ! Cartographer.UpdateMarkers(chapter, tree)

    case Discard ⇒
      sender ! ()
      saved = true
      backupFile.delete()
      Ui(service.stopSelf()).run

    case Save(id) ⇒
      // TODO: add to existing story
      val story = Story.empty(id).withChapter(chapter.finish).withStudyInfo(studyInfo).withAuthor(app.author)
      app.hybridApi.createStory(story)
      sender ! ()
      saved = true
      backupFile.delete()
      Ui(service.stopSelf()).run

    case QueryBackup ⇒
      val answer = !restoreSuggested && backupFile.exists()
      if (!answer) restoreSuggested = true
      sender ! answer

    case Backup ⇒
      if (!saved && restoreSuggested) {
        import net.routestory.json.JsonWrites._
        val json = To[Story.Chapter, JsObject](chapter).toString()
        IOUtils.write(json, new FileOutputStream(backupFile), "UTF-8")
      }

    case RestoreBackup ⇒
      restoreSuggested = true
      app.hybridApi.restoreChapter(backupFile).go
        .map(Restored)
        .pipeTo(self)

    case DiscardBackup ⇒
      restoreSuggested = true
      backupFile.delete()
  }
}
