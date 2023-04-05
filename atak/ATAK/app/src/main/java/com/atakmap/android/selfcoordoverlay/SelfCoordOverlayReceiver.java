
package com.atakmap.android.selfcoordoverlay;

/**
 * Interface for receiving self coordinate overlay updates
 */
public interface SelfCoordOverlayReceiver {

    /**
     * Self coordinate text has been changed
     * @param text Self coordinate text
     */
    void onSelfCoordinateChanged(SelfCoordText text);
}
