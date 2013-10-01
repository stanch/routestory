package net.routestory

import android.app.Application
import android.content.Context
import android.content.Context._
import android.net.ConnectivityManager
import net.routestory.model._
import java.io.InputStream
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.lounge.Lounge
import rx.{ Obs, Var }

object StoryApplication {
  val storyPreviewDuration = 30
}

class StoryApplication extends Application with Lounge {
  implicit val ctx = this
  lazy val authToken = Var {
    Option(getSharedPreferences("default", MODE_PRIVATE)).map(_.getString("authToken", "")).filter(!_.isEmpty)
  }
  lazy val authorId = Var {
    Option(getSharedPreferences("default", MODE_PRIVATE)).map(_.getString("authorId", "")).filter(!_.isEmpty)
  }

  def setAuthData(s: Option[Array[String]]) {
    authorId.update(s.map(_(0)))
    authToken.update(s.map(_(1)))
    val prefs = getSharedPreferences("default", MODE_PRIVATE)
    val editor = prefs.edit()
    editor.putString("authToken", authToken.now.getOrElse(""))
    editor.putString("authorId", authorId.now.getOrElse(""))
    editor.commit()
    sync()
  }

  def signOut() {
    setAuthData(None)
  }

  def isSignedIn = authToken.now.isDefined

  def getAuthor = Local.couch.map(c ⇒ authorId.now.map(c.get(classOf[Author], _)))

  def createStory(story: Story) = Local.couch.map(_.create(story))

  def updateStoryAttachment(attachmentId: String, contentStream: InputStream, contentType: String, id: String, rev: String) = {
    Local.updateAttachment(attachmentId, contentStream, contentType, id, rev)
  }

  def compactLocal() = Local.server.map(_.getDatabaseNamed("story").compact())

  def deleteStory(story: Story) = Local.couch.map(_.delete(story))

  def isOnline = {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
    Option(cm.getActiveNetworkInfo).exists(_.isConnected)
  }
}