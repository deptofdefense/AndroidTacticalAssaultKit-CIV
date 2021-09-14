
package com.atakmap.android.helloworld.recyclerview;

import android.content.Context;
import android.util.AttributeSet;

/**
 * RecyclerView override for use with ATAK
 */
public class RecyclerView extends androidx.recyclerview.widget.RecyclerView {

    private static final String TAG = "RecyclerView";

    public RecyclerView(Context context) {
        super(context);
    }

    public RecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void scrollTo(int x, int y) {
        // Not supported and causes a crash when called
        // Samsung likes to call this method directly outside of our control
        // So we need to override it with a no-op
    }
}
