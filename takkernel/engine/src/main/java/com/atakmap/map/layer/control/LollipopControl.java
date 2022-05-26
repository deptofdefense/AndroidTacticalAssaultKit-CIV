package com.atakmap.map.layer.control;

import com.atakmap.map.MapControl;

/**
 * A rendering control to make sure that global decisions can be made about
 * the rendering of the altitude lollipops when a marker is above ground
 */
public interface LollipopControl extends MapControl {
    /**
     * Check the global status of the lollipops visibility.
     * @return true if lollipops are visible.
     */
    boolean getLollipopsVisible();

    /**
     * Sets the status of the lollipops.
     * @param v true if the lollipops should be visible.
     */
    void setLollipopsVisible(boolean v);
}
