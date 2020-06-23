
package com.atakmap.android.widgets;

import android.graphics.Color;

public class ProgressWidget extends LayoutWidget {

    private int max, min, progress;
    private int color = DEFAULT_PROGRESS_BAR_COLOR;
    public static int DEFAULT_PROGRESS_BAR_COLOR = Color.DKGRAY;

    private final LayoutWidget progressBar;

    public ProgressWidget() {
        progressBar = new LayoutWidget();
        progressBar.setBackingColor(color);
        progressBar.setWidth(0);
        addWidget(progressBar);
    }

    /**
     * Return the upper limit of this progress bar's range.=
     *
     * @return a positive integer
     */
    public int getMax() {
        return max;
    }

    /**
     * <p>Set the upper range of the progress bar <tt>max</tt>.</p>
     *
     * @param value the upper range of this progress bar
     */
    public void setMax(int value) {
        max = value;
        if (progress > max)
            progress = max;
    }

    /**
     * Return the lower limit of this progress bar's range.=
     *
     * @return a positive integer
     */
    public int getMin() {
        return min;
    }

    /**
     * Set the lower range of the progress bar to <tt>min</tt>.
     *
     * @param value the lower range of this progress bar
     */
    public void setMin(int value) {
        min = value;
        if (progress < min)
            progress = min;
        redraw();
    }

    /**
     * Get the progress bar's current level of progress.
     *
     * @return the current progress, between {@link #getMin()} and {@link #getMax()}
     */
    public int getProgress() {
        return progress;
    }

    /**
     * Sets the current progress to the specified value
     */
    public boolean setProgress(int value) {
        value = Math.max(min, Math.min(value, max));
        if (value == progress)
            return false;
        else {
            progress = value;
            redraw();
            return true;
        }
    }

    @Override
    public boolean setWidth(float width) {
        boolean retval = super.setWidth(width);
        redraw();
        return retval;
    }

    @Override
    public boolean setHeight(float height) {
        boolean retval = super.setHeight(height);
        progressBar.setHeight(height);
        return retval;
    }

    /**
     * Sets the color of the progress bar.
     * @param color the color to use.
     */
    public void setProgressBarColor(final int color) {
        this.color = color;
        progressBar.setBackingColor(color);
    }

    private void redraw() {
        progressBar
                .setWidth(getWidth() * (progress - min) / (float) (max - min));
    }
}
