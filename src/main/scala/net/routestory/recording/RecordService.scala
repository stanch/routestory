package net.routestory.recording

import akka.actor.{ ActorSystem, ActorRef }
import android.app.{ NotificationManager, PendingIntent, Service }
import android.content.{ Context, Intent }
import android.location.{ Location }
import android.os.{ Bundle, Parcel, Binder, IBinder }
import android.support.v4.app.{ NotificationCompat, TaskStackBuilder }
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesClient.{ OnConnectionFailedListener, ConnectionCallbacks }
import com.google.android.gms.location.{ LocationListener, LocationRequest, LocationClient }
import com.typesafe.config.ConfigFactory
import macroid.{ AutoLogTag, AppContext }
import net.routestory.{ RouteStoryApp, R }
import net.routestory.recording.logged.Dictaphone
import net.routestory.recording.suggest.Suggester
import macroid.FullDsl._

trait LocationListening { self: RecordService ⇒
  lazy val locationClient = new LocationClient(
    getApplicationContext,
    locationConnectionCallbacks,
    locationConnectionFailedListener
  )

  object locationConnectionCallbacks extends ConnectionCallbacks {
    override def onConnected(bundle: Bundle) = {
      val request = LocationRequest.create()
        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        .setInterval(5000) // 5 seconds
        .setFastestInterval(5000) // 5 seconds
      locationClient.requestLocationUpdates(request, locationListener)
    }
    override def onDisconnected() = ()
  }

  object locationConnectionFailedListener extends OnConnectionFailedListener {
    override def onConnectionFailed(result: ConnectionResult) = ()
  }

  object locationListener extends LocationListener {
    override def onLocationChanged(location: Location) = cartographer ! Cartographer.Locate(location)
  }
}

class RecordService extends Service with LocationListening with AutoLogTag { self ⇒
  lazy val actorSystem = ActorSystem(
    "recording",
    ConfigFactory.load(getApplication.getClassLoader),
    getApplication.getClassLoader
  )

  implicit lazy val appCtx = AppContext(getApplicationContext)

  lazy val typewriter = actorSystem.actorOf(Typewriter.props, "typewriter")
  lazy val cartographer = actorSystem.actorOf(Cartographer.props, "cartographer")
  lazy val suggester = actorSystem.actorOf(Suggester.props(getApplication.asInstanceOf[RouteStoryApp]), "suggester")
  lazy val dictaphone = actorSystem.actorOf(Dictaphone.props, "dictaphone")

  override def onCreate() = {
    logW"RecordService created"()
    super.onCreate()
    addNotification()
    (typewriter, cartographer, suggester, dictaphone)
    locationClient.connect()
  }

  override def onDestroy() = {
    logW"RecordService destroyed"()
    locationClient.disconnect()
    actorSystem.shutdown()
    removeNotification()
    super.onDestroy()
  }

  def addNotification() = {
    val pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, classOf[RecordActivity]), 0)

    val n = new NotificationCompat.Builder(this)
      .setOngoing(true)
      .setSmallIcon(R.drawable.ic_launcher_notif)
      .setContentTitle("RouteStory")
      .setContentText("Recording a story")
      .setContentIntent(pendingIntent)
      .build()

    val manager = getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    manager.notify(0, n)
  }

  def removeNotification() = {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    manager.cancel(0)
  }

  override def onStartCommand(intent: Intent, flags: Int, startId: Int) = {
    super.onStartCommand(intent, flags, startId)
    if (intent == null) stopSelf()
    Service.START_STICKY
  }

  class RecordBinder extends Binder {
    lazy val actorSystem = self.actorSystem
  }

  override def onBind(intent: Intent) = new RecordBinder
}
