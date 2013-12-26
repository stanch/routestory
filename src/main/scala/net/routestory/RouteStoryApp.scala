package net.routestory

import android.app.Application
import android.content.Context
import android.content.Context._
import android.net.ConnectivityManager
import rx.{ Obs, Var }
import android.util.Log
import com.typesafe.config.ConfigFactory
import net.routestory.lounge.Couch
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.needs.{ AppContext, NeedAuthor }

class RouteStoryApp extends Application with Couch {
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
  def author = authorId.now.map(id ⇒ NeedAuthor(id)(AppContext(this)).go)

  def isOnline = {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
    Option(cm.getActiveNetworkInfo).exists(_.isConnected)
  }
}