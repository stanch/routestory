package org.macroid

import android.app.Fragment
import android.widget.FrameLayout
import android.content.Context

trait Fragments { self: ViewSearch ⇒
    def fragment(frag: ⇒ Fragment, id: Int, tag: String)(implicit ctx: Context): FrameLayout = {
        Option(findFrag(tag)) getOrElse {
            val fragmentTransaction = fragmentManager.beginTransaction()
            fragmentTransaction.add(id, frag, tag)
            fragmentTransaction.commit()
        }
        new FrameLayout(ctx) { setId(id) }
    }
}