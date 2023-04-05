
package com.atakmap.android.user.feedback;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * Relative layout that's always a square
 */
public class FeedbackGalleryItemLayout extends RelativeLayout {

    public FeedbackGalleryItemLayout(Context context) {
        super(context);
    }

    public FeedbackGalleryItemLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FeedbackGalleryItemLayout(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //noinspection SuspiciousNameCombination
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
}
