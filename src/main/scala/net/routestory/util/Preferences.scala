package net.routestory.util

import android.preference.PreferenceManager
import macroid.AppContext

object Preferences {
  def undefined(id: String)(implicit ctx: AppContext) =
    !PreferenceManager.getDefaultSharedPreferences(ctx.get)
      .getBoolean(id, false)

  def define(id: String)(implicit ctx: AppContext) =
    PreferenceManager.getDefaultSharedPreferences(ctx.get)
      .edit().putBoolean(id, true).commit()
}
