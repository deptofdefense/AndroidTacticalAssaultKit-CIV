
package com.atakmap.android.gui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.RadioButton;

import com.atakmap.app.R;

/**
 * Creates an image radio button with the specified image centered on the button
 * No text, and specify the image through drawable attribute
 *
 *
 */
public class ImageRadioButton extends RadioButton {

    private final Drawable _buttonDrawable;

    public ImageRadioButton(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        TypedArray a = context.obtainStyledAttributes(attributeSet, new int[] {
                R.drawable.btn_default
        }, 0, 0);
        _buttonDrawable = a.getDrawable(1); //Grab the image to be drawn
        setButtonDrawable(android.R.color.transparent); //Clear it out as it's in the wrong spot (We'll redraw correctly)
        a.recycle(); //Make sure to cleanup
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (_buttonDrawable != null) {
            int height = _buttonDrawable.getIntrinsicHeight() - getPaddingTop()
                    - getPaddingBottom();
            int width = _buttonDrawable.getIntrinsicWidth() - getPaddingRight()
                    - getPaddingLeft();
            int left = (getHeight() - height) / 2;
            int top = (getWidth() - width) / 2;
            _buttonDrawable.setBounds(left, top, left + width, top + height);
            _buttonDrawable.draw(canvas);
        }
    }

}
