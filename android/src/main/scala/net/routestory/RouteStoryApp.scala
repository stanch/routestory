package net.routestory

import android.app.Application
import android.preference.PreferenceManager
import android.util.Log
import com.loopj.android.http.AsyncHttpClient
import net.routestory.data.Author
import scala.concurrent.ExecutionContext.Implicits.global

class RouteStoryApp extends Application with Couch with Apis {
  override def onCreate() = {
    super.onCreate()
    setCouchViews()
  }

  def prefs = PreferenceManager.getDefaultSharedPreferences(this)

  def author = Option(prefs.getString("author", null))
    .map(Author(_, "", None, None))

  def setAuthor(author: String) =
    prefs.edit().putString("author", author).commit()

  def register() = {
    author foreach { a ⇒
      Log.d("Regist", s"registering $a")
      resolvable.http.AndroidClient(new AsyncHttpClient)
        .get("https://routestory.herokuapp.com/register", Map("id" → a.id))
        .foreach { response ⇒
          Log.d("Regist", s"got response $response")
          setRegistered()
          setSuggestionsEnabled(response == "true")
        }
    }
  }

  def registered =
    prefs.getBoolean("registered", false)

  def setRegistered() =
    prefs.edit().putBoolean("registered", true).commit()

  def suggestionsEnabled =
    prefs.getBoolean("suggestionsEnabled", false)

  def setSuggestionsEnabled(b: Boolean) =
    prefs.edit().putBoolean("suggestionsEnabled", b).commit()
}
