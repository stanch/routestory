package net.routestory.recording

import akka.actor.ActorSystem
import android.app.{ NotificationManager, PendingIntent, Service }
import android.content._
import android.os.Binder
import android.support.v4.app.NotificationCompat
import com.typesafe.config.ConfigFactory
import macroid.FullDsl._
import macroid.{ AppContext, AutoLogTag }
import net.routestory.recording.logged.{ Dictaphone, Locator }
import net.routestory.recording.suggest.Suggester
import net.routestory.{ R, RouteStoryApp }

class RecordService extends Service with AutoLogTag { self ⇒
  lazy val actorSystem = ActorSystem(
    "recording",
    ConfigFactory.load(getApplication.getClassLoader),
    getApplication.getClassLoader
  )

  implicit lazy val appCtx = AppContext(getApplicationContext)

  lazy val app = getApplication.asInstanceOf[RouteStoryApp]

  lazy val typewriter = actorSystem.actorOf(Typewriter.props(this, app), "typewriter")
  lazy val cartographer = actorSystem.actorOf(Cartographer.props, "cartographer")
  lazy val suggester = actorSystem.actorOf(Suggester.props(app), "suggester")
  lazy val dictaphone = actorSystem.actorOf(Dictaphone.props, "dictaphone")

  lazy val locator = new Locator

  override def onCreate() = {
    logW"RecordService created"()
    super.onCreate()
    addNotification()
    (typewriter, cartographer, suggester, dictaphone)
    locator.connect()
  }

  override def onDestroy() = {
    logW"RecordService destroyed"()
    locator.disconnect()
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
