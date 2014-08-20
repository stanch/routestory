package net.routestory.ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.{ ViewGroup, LayoutInflater }
import com.etsy.android.grid.StaggeredGridView
import macroid.Contexts
import macroid.FullDsl._

trait StaggeredFragment extends Fragment with Contexts[Fragment] {
  var grid = slot[StaggeredGridView]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    w[StaggeredGridView] <~ wire(grid) <~ Styles.grid
  }
}
