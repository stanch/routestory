package net.routestory.parts;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;

public class HapticButton extends Button {
	public HapticButton(Context context) {
		super(context);
	}
	
	public HapticButton (Context context, AttributeSet attrs) {
	    super(context, attrs);
	}

	public HapticButton (Context context, AttributeSet attrs, int defStyle) {
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
