package net.routestory

import android.app.Application

class RouteStoryApp extends Application with Couch with Apis {
  override def onCreate() = {
    super.onCreate()
    setCouchViews()
  }
}
