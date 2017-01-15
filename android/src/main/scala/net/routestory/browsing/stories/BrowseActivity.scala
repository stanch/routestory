package net.routestory.browsing.stories

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.{ Menu, MenuItem }
import com.google.android.gms.common.{ AccountPicker, ConnectionResult, GooglePlayServicesUtil }
import macroid.FullDsl._
import macroid.IdGeneration
import net.routestory.R
import net.routestory.recording.RecordActivity
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }

object BrowseActivity {
  val requestCodePickAccount = 0
}

class BrowseActivity extends RouteStoryActivity with FragmentPaging with IdGeneration {
  var signingIn = false

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    // install Google Play Services if needed
    val result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)
    if (result != ConnectionResult.SUCCESS) {
      GooglePlayServicesUtil.getErrorDialog(result, this, 0).show()
    }

    signingIn = Option(savedInstanceState)
      .exists(_.getBoolean("signingIn", false))

    // setup account and register
    if (app.author.isDefined) {
      if (!app.registered) {
        app.register()
      }
    } else if (!signingIn) {
      val intent = AccountPicker.newChooseAccountIntent(null, null, Array("com.google"), false, null, null, null, null)
      signingIn = true
      startActivityForResult(intent, BrowseActivity.requestCodePickAccount)
    }

    // show UI
    bar.setHomeButtonEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)

    setContentView(getUi(drawer(getTabs(
      "My stories" → f[LocalFragment].factory,
      "Stories online" → f[OnlineFragment].pass("number" → 10).factory
    ))))
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.activity_explore, menu)
    //setupSearch(menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.create ⇒
      startActivity(new Intent(this, classOf[RecordActivity])); true
    case _ ⇒
      super[RouteStoryActivity].onOptionsItemSelected(item)
  }

  override def onSaveInstanceState(outState: Bundle) = {
    outState.putBoolean("signingIn", signingIn)
    super.onSaveInstanceState(outState)
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == BrowseActivity.requestCodePickAccount) {
      signingIn = false
      if (resultCode == Activity.RESULT_OK) {
        val email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        app.setAuthor(email)
        app.register()
      }
    }
  }
}
