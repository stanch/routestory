package net.routestory.ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.view.{ View, ViewGroup, LayoutInflater }
import com.etsy.android.grid.StaggeredGridView
import macroid.{ Ui, Contexts }
import macroid.FullDsl._

trait StaggeredFragment extends Fragment with Contexts[Fragment] {
  var grid = slot[StaggeredGridView]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    w[StaggeredGridView] <~ wire(grid) <~ Styles.grid
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
}
