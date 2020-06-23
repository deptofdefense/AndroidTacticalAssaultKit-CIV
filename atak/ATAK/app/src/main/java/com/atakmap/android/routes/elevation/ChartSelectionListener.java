
package com.atakmap.android.routes.elevation;

/**
 * This interface provides the ability to alert a listener as to when counts changed.
 */
public interface ChartSelectionListener {
    /**
     * Method for updating a single series
     */
    void update(int index, double xVal, double yVal, boolean moveSeeker,
            boolean seekerStopped);
}
