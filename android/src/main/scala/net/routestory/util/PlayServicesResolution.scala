package net.routestory.util

import android.app.{ PendingIntent, Activity }
import android.content.Intent
import android.os.Bundle
import com.google.android.gms.common.{ GooglePlayServicesUtil, ConnectionResult }
import macroid.AppContext

class PlayServicesResolutionActivity extends Activity {
  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    val errorCode = getIntent.getIntExtra("errorCode", 0)
    val resolution = getIntent.getParcelableExtra[PendingIntent]("resolution")
    val result = new ConnectionResult(errorCode, resolution)
    if (result.hasResolution) {
      result.startResolutionForResult(this, 0)
    } else {
      GooglePlayServicesUtil.getErrorDialog(errorCode, this, 0).show()
    }
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = requestCode match {
    case 0 ⇒ finish()
    case _ ⇒ super.onActivityResult(requestCode, resultCode, data)
  }
}

object PlayServicesResolution {
  def resolve(result: ConnectionResult)(implicit ctx: AppContext) = {
    val intent = new Intent(ctx.get, classOf[PlayServicesResolutionActivity])
      .putExtra("errorCode", result.getErrorCode)
      .putExtra("resolution", result.getResolution)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.get.startActivity(intent)
  }
}
