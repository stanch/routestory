package org.macroid

import android.view.View
import android.app.{ Activity, Fragment, FragmentManager }

sealed trait ViewSearch {
    def fragmentManager: FragmentManager

    def findView[A <: View](id: Int): A
    def findFrag[A <: Fragment](tag: String) = fragmentManager.findFragmentByTag(tag).asInstanceOf[A]
}

trait ActivityViewSearch extends ViewSearch { self: Activity ⇒
    def fragmentManager = getFragmentManager
    def findView[A <: View](id: Int) = findViewById(id).asInstanceOf[A]
}

trait FragmentViewSearch extends ViewSearch { self: Fragment ⇒
    def fragmentManager = getChildFragmentManager
    def findView[A <: View](id: Int) = getView.findViewById(id).asInstanceOf[A]
}