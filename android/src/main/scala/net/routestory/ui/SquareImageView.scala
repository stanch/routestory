package net.routestory.ui

import android.content.Context
import android.view.View.MeasureSpec
import android.widget.ImageView

class SquareImageView(ctx: Context) extends ImageView(ctx) {
  override def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    setMeasuredDimension(width, width)
  }
}
