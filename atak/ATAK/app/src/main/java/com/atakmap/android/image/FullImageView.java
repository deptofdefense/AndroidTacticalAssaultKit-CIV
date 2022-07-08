
package com.atakmap.android.image;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * An imageview that is able to set snap to the width in both directions for appropriate grid view
 * layout in something like a gallery.
 */
public class FullImageView extends ImageView {

    public FullImageView(Context context) {
        super(context);
    }

    public FullImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FullImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth()); //Snap to width
    }
}
