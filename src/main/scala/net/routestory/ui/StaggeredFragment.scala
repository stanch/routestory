package net.routestory.ui

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.view.{ View, ViewGroup, LayoutInflater }
import android.widget.FrameLayout
import com.etsy.android.grid.StaggeredGridView
import macroid.{ Ui, Contexts }
import macroid.FullDsl._
import macroid.contrib.ListTweaks

trait StaggeredFragment extends Fragment with Contexts[Fragment] {
  var frame = slot[FrameLayout]
  var grid = slot[StaggeredGridView]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    l[FrameLayout](
      w[StaggeredGridView] <~ wire(grid) <~ Styles.grid
    ) <~ wire(frame)
  }

  override def onConfigurationChanged(newConfig: Configuration) = {
    super.onConfigurationChanged(newConfig)
    val adapter = grid.flatMap(g ⇒ Option(g.getAdapter))
    val newGrid = w[StaggeredGridView] <~ wire(grid) <~ Styles.grid <~ adapter.map(ListTweaks.adapter)
    runUi(frame <~ addViews(Seq(newGrid), removeOld = true))
  }
}

trait SwipingStaggeredFragment extends Fragment with Contexts[Fragment] {
  var swiper = slot[SwipeRefreshLayout]
  var grid = slot[StaggeredGridView]

  def refresh: Ui[Any]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[SwipeRefreshLayout](
      w[StaggeredGridView] <~ wire(grid) <~ Styles.grid
    ) <~ Styles.swiper <~ On.refresh[SwipeRefreshLayout](refresh) <~ wire(swiper)
  }

  override def onConfigurationChanged(newConfig: Configuration) = {
    super.onConfigurationChanged(newConfig)
    val adapter = grid.flatMap(g ⇒ Option(g.getAdapter))
    val newGrid = w[StaggeredGridView] <~ wire(grid) <~ Styles.grid <~ adapter.map(ListTweaks.adapter)
    runUi(swiper <~ addViews(Seq(newGrid), removeOld = true))
  }
}
