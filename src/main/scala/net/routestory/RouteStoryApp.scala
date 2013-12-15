package net.routestory

import android.app.Application
import android.content.Context
import android.content.Context._
import android.net.ConnectivityManager
import rx.{ Obs, Var }
import android.util.Log
import com.typesafe.config.ConfigFactory

class RouteStoryApp extends Application {
  implicit val ctx = this
  lazy val authToken = Var(readPrefs("authToken"))
  lazy val authTokenSaver = authToken.foreach(writePrefs("authToken", _), skipInitial = true)
  lazy val authorId = Var(readPrefs("authorId"))
  lazy val authorIdSaver = authorId.foreach(writePrefs("authorId", _), skipInitial = true)

  val config = ConfigFactory.load()

  override def onCreate() {
    super.onCreate()
    authTokenSaver; authorIdSaver // touch observers
    //init
  }

  def readPrefs(key: String) = {
    val value = Option(getSharedPreferences("default", MODE_PRIVATE)).map(_.getString(key, "")).filter(!_.isEmpty)
    Log.d("Prefs", s"reading $key: $value from prefs"); value
  }
  def writePrefs(key: String, value: Option[String]) {
    Log.d("Prefs", s"writing $key: $value to prefs")
    val prefs = getSharedPreferences("default", MODE_PRIVATE)
    prefs.edit().putString(key, value.getOrElse("")).commit()
  }

  //  def setAuthData(s: Option[Array[String]]) = signIn(s.map(_(1)), s.map(_(0)))
  //
  //  def getAuthor = Local.couch.map(c ⇒ authorId.now.map(c.get(classOf[Author], _)))
  //
  //  def createStory(story: Story) = Local.couch.map(_.create(story))

  //  def updateStoryAttachment(attachmentId: String, contentStream: InputStream, contentType: String, id: String, rev: String) = {
  //    Local.updateAttachment(attachmentId, contentStream, contentType, id, rev)
  //  }

  //  def compactLocal = Local.server.map(_.getDatabaseNamed("story").compact())
  //
  //  def deleteStory(story: Story) = Local.couch.map(_.delete(story))

  def isOnline = {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
    Option(cm.getActiveNetworkInfo).exists(_.isConnected)
  }
}