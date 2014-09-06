package net.routestory.ui

import java.io.File

import android.support.v4.widget.SwipeRefreshLayout
import android.widget.ImageView
import com.squareup.picasso.Picasso
import macroid.{ AppContext, Tweak }

object Tweaks {
  val stopRefresh = Tweak[SwipeRefreshLayout](_.setRefreshing(false))
  val startRefresh = Tweak[SwipeRefreshLayout](_.setRefreshing(true))

  def picasso(file: File)(implicit appCtx: AppContext) = Tweak[ImageView] { x ⇒
    Picasso.`with`(appCtx.get)
      .load(file)
      .fit()
      .centerInside()
      .into(x)
  }
}
