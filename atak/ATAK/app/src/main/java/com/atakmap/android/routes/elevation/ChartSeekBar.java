
package com.atakmap.android.routes.elevation;

import android.content.Context;
import android.view.View;
import android.widget.SeekBar;

/**
 * This class extends the SeekBar so that the margins and max values can be adjusted.
 */
public class ChartSeekBar extends SeekBar {
    /**
     * These variables are used to adjust the max of the seek bar based on the margins.
     */
    private int _leftMarginAdjust = 0;
    private int _rightMarginAdjust = 0;

    public ChartSeekBar(Context context) {
        super(context);

        this.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i2, int i3,
                    int i4, int i5, int i6,
                    int i7, int i8) {
                refreshMargins();
            }
        });
    }

    /**
     * @return the _leftMarginAdjust
     */
    public int getLeftMarginAdjust() {
        return _leftMarginAdjust;
    }

    /**
     * @param leftMarginAdjust the _leftMarginAdjust to set
     */
    public void setLeftMarginAdjust(int leftMarginAdjust) {
        this._leftMarginAdjust = leftMarginAdjust;
    }

    /**
     * @return the _rightMarginAdjust
     */
    public int getRightMarginAdjust() {
        return _rightMarginAdjust;
    }

    /**
     * @param rightMarginAdjust the _rightMarginAdjust to set
     */
    public void setRightMarginAdjust(int rightMarginAdjust) {
        this._rightMarginAdjust = rightMarginAdjust;
    }

    public void refreshMargins() {
        this.setMax(this.getWidth() - getLeftMarginAdjust()
                - getRightMarginAdjust());
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        // Adjusts the max of this seek bar to width of the component
        // but adjusted for the left/right margins.
        this.setMax(this.getWidth() - getLeftMarginAdjust()
                - getRightMarginAdjust());
    }

}
