package net.routestory

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SyncService extends Service {
  lazy val app = getApplication.asInstanceOf[StoryApplication]

  def onBind(p1: Intent): IBinder = null

  override def onCreate() {
    super.onCreate()
    getApplication
  }

  override def onStartCommand(intent: Intent, flags: Int, startId: Int) = {
    super.onStartCommand(intent, flags, startId)
  }
}
