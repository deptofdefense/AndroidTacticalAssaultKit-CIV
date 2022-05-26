package com.atakmap.map.layer.control;

import com.atakmap.map.MapControl;

/**
 * A rendering control to make sure that global decisions can be made about
 * the rendering of map items when the view is in NADIR
 */
public interface ClampToGroundControl extends MapControl {

    /**
     * Sets the global status of the markers to clamp ground when looking straight down from overhead.
     * @param v true if the marker should be clamped to ground when looking straight down.
     */
    void setClampToGroundAtNadir(boolean v);

    /**
     * Checks the global status of the clamp to ground setting for Markers.
     * @return true if at least one marker is clamped to ground
     */
    boolean getClampToGroundAtNadir();
}
