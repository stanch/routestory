package net.routestory.util

import android.preference.PreferenceManager
import macroid.AppContext

object Preferences {
  def onlyOnce[A](id: String)(code: ⇒ A)(implicit ctx: AppContext) = {
    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx.get)
    if (!prefs.getBoolean(id, false)) {
      prefs.edit().putBoolean(id, true).commit()
      code
    }
  }
}
