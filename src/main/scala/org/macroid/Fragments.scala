package org.macroid

import android.app.Fragment
import android.widget.FrameLayout
import android.content.Context
import android.view.View

trait Fragments { self: ViewSearch ⇒
  def fragment(frag: ⇒ Fragment, id: Int, tag: String, hide: Boolean = false)(implicit ctx: Context): FrameLayout = {
    Option(findFrag(tag)) getOrElse {
      val fragmentTransaction = fragmentManager.beginTransaction()
      fragmentTransaction.add(id, frag, tag)
      fragmentTransaction.commit()
    }
    new FrameLayout(ctx) {
      setId(id)
      if (hide) setVisibility(View.GONE)
    }
  }
}