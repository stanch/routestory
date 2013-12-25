package net.routestory.ui

import android.content.Context
import android.widget.Button
import android.view.View.OnClickListener
import android.view.{ HapticFeedbackConstants, View }

class HapticButton(ctx: Context) extends Button(ctx) {
  override def setOnClickListener(l: OnClickListener) {
    super.setOnClickListener(new OnClickListener {
      def onClick(v: View) {
        v.setHapticFeedbackEnabled(true)
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        l.onClick(v)
      }
    })
  }
}
