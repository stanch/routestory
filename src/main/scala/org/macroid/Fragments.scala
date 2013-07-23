package org.macroid

import android.app.{ FragmentTransaction, ActionBar, Fragment }
import android.widget.FrameLayout
import android.content.Context
import android.view.View
import android.app.ActionBar.Tab

trait Fragments { self: ViewSearch ⇒
  def fragment(frag: ⇒ Fragment, id: Int, tag: String, hide: Boolean = false)(implicit ctx: Context): FrameLayout = {
    Option(findFrag[Fragment](tag)) getOrElse {
      fragmentManager.beginTransaction().add(id, frag, tag).commit()
    }
    new FrameLayout(ctx) {
      setId(id)
      if (hide) setVisibility(View.GONE)
    }
  }

  class TabListener(frag: ⇒ Fragment, tag: String) extends ActionBar.TabListener {
    def onTabSelected(tab: Tab, ft: FragmentTransaction) {
      Option(findFrag[Fragment](tag)) map {
        ft.attach(_)
      } getOrElse {
        ft.add(android.R.id.content, frag, tag)
      }
    }

    def onTabUnselected(tab: Tab, ft: FragmentTransaction) {
      Option(findFrag[Fragment](tag)).map(ft.detach(_))
    }

    def onTabReselected(p1: Tab, p2: FragmentTransaction) {}
  }

  implicit class RichActionBar(bar: ActionBar) {
    def addTab(title: CharSequence, frag: ⇒ Fragment, tag: String, focus: Boolean) {
      bar.addTab(bar.newTab().setText(title).setTabListener(new TabListener(frag, tag)), focus)
    }
    def addTab(title: Int, frag: ⇒ Fragment, tag: String, focus: Boolean) {
      bar.addTab(bar.newTab().setText(title).setTabListener(new TabListener(frag, tag)), focus)
    }
  }
}