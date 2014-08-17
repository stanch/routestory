package net.routestory.recording.logged

import android.app.PendingIntent
import android.content._
import android.location.Location
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesClient.{ ConnectionCallbacks, OnConnectionFailedListener }
import com.google.android.gms.location.{ LocationClient, LocationRequest }
import macroid.AppContext
import net.routestory.recording.{ Cartographer, RecordService }

class Locator(implicit ctx: AppContext) {
  val locationClient = new LocationClient(ctx.get, connectionCallbacks, connectionFailedListener)

  val intent = new Intent(ctx.get, classOf[LocationBroadcastReceiver])
  val pendingIntent = PendingIntent.getBroadcast(ctx.get, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
  val request = LocationRequest.create()
    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
    .setInterval(10000) // 10 seconds
    .setFastestInterval(5000) // 5 seconds

  object connectionCallbacks extends ConnectionCallbacks {
    def onConnected(bundle: Bundle) = locationClient.requestLocationUpdates(request, pendingIntent)
    def onDisconnected() = locationClient.removeLocationUpdates(pendingIntent)
  }

  object connectionFailedListener extends OnConnectionFailedListener {
    // TODO: ???
    def onConnectionFailed(result: ConnectionResult) = ()
  }
}

class LocationBroadcastReceiver extends BroadcastReceiver {
  def onReceive(context: Context, intent: Intent) = {
    val location = intent.getExtras.get(LocationClient.KEY_LOCATION_CHANGED).asInstanceOf[Location]
    Option(peekService(context, new Intent(context, classOf[RecordService]))) foreach { binder ⇒
      val actorSystem = binder.asInstanceOf[RecordService#RecordBinder].actorSystem
      actorSystem.actorSelection("/user/cartographer") ! Cartographer.Locate(location)
    }
  }
}
