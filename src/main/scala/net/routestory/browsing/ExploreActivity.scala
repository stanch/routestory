package net.routestory.browsing

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.{ Menu, MenuItem }

import org.macroid.LayoutDsl._
import com.google.android.gms.common.{ ConnectionResult, GooglePlayServicesUtil }

import net.routestory.R
import net.routestory.recording.RecordActivity
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import org.macroid.IdGeneration
import android.widget.{ ImageView, FrameLayout }
import android.util.Log
import net.routestory.util.BitmapUtils

class ExploreActivity extends RouteStoryActivity with FragmentPaging with IdGeneration {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    // install Google Play Services if needed
    val result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)
    if (result != ConnectionResult.SUCCESS) {
      GooglePlayServicesUtil.getErrorDialog(result, this, 0).show()
    }

    // set default preferences
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

    import scala.concurrent.ExecutionContext.Implicits.global

    // show UI
    bar.setHomeButtonEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    //    setContentView {
    //      w[ImageView] ~>
    //        app.api.RemoteExternalMedia("https://lh4.googleusercontent.com/-WKu4S1JqD14/AAAAAAAAAAI/AAAAAAAAACI/1D1wvUBC_q0/photo.jpg").probe.go.map {
    //          BitmapUtils.decodeFile(_, 300)
    //        }.map {
    //          org.macroid.contrib.ExtraTweaks.Image.bitmap
    //        }
    //    }

    //app.api.NeedMedia("https://precisionconference.com/~sigchi/style/logoMOBILEHCI14.png").go map { f ⇒
    //    app.api.RemoteExternalMedia("https://precisionconference.com/~sigchi/style/logoMOBILEHCI14.png").probe.go map { f ⇒
    //      Log.d("Needs", s"downloaded $f")
    //    } recover {
    //      case t ⇒ t.printStackTrace(); throw t
    //    }

    setContentView(drawer(getTabs(
      "Latest" → f[LatestStoriesFragment].pass("number" → 10).factory,
      "Popular tags" → f[TagsFragment].factory
    )))
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.activity_explore, menu)
    setupSearch(menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.create ⇒
      startActivity(new Intent(this, classOf[RecordActivity])); true
    case _ ⇒
      super[RouteStoryActivity].onOptionsItemSelected(item)
  }
}
