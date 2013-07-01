package net.routestory.parts;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageButton;

public class HapticImageButton extends ImageButton {
	public HapticImageButton(Context context) {
		super(context);
	}
	
	public HapticImageButton (Context context, AttributeSet attrs) {
	    super(context, attrs);
	}

	public HapticImageButton (Context context, AttributeSet attrs, int defStyle) {
	    super(context, attrs, defStyle);
	}
	
	@Override
	public void setOnClickListener(final View.OnClickListener l) {
		super.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				v.setHapticFeedbackEnabled(true);
				v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
				l.onClick(v);
			}
		});
	}
}