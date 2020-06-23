
package com.atakmap.android.widgets;

import android.graphics.Color;

public class TimeProgressWidget extends LayoutWidget {

    private int millis, curr;
    private int color = DEFAULT_PROGRESS_BAR_COLOR;
    public static int DEFAULT_PROGRESS_BAR_COLOR = Color.DKGRAY;
    private Thread t;
    private boolean forward = true;

    private final LayoutWidget progressBar;

    public TimeProgressWidget() {
        progressBar = new LayoutWidget();
        progressBar.setBackingColor(color);
        progressBar.setWidth(0);
        addWidget(progressBar);
    }

    /**
     * Set time in millis that this TimeProgress widget runs to.
     */
    public void setMilliseconds(final int millis) {
        this.millis = millis;
    }

    /**
     * Get the progress bar's current level of progress.
     *
     * @return the current progress, in milliseconds
     */
    public int getProgress() {
        return Math.min(curr, millis);
    }

    public void start() {
        if (t != null)
            return;

        t = new Thread() {
            public void run() {
                curr = 0;
                do {
                    redraw();
                    try {
                        Thread.sleep(50);
                    } catch (Exception ignored) {
                    }
                    curr += 50;
                } while (millis >= curr);
                t = null;
            }
        };
        t.start();
    }

    public void stop() {
        curr = millis;
        t = null;
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

    /**
     * Direction that the progress bar heads when the millis gets closer.
     * @param forward true if the progress bar increases in size, false if it decreases in size.
     */
    public void setDirection(final boolean forward) {
        this.forward = forward;
    }

    /**
     * Get the current progress bar direction.
     */
    public boolean getDirection() {
        return forward;
    }

    private void redraw() {
        if (forward)
            progressBar.setWidth(getWidth() * (curr) / (float) (millis));
        else
            progressBar
                    .setWidth(getWidth() * (millis - curr) / (float) (millis));

    }
}
