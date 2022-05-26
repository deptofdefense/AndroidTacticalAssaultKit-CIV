
package com.atakmap.android.navigation.views.buttons;

/**
 * Listener for nav buttons visibility changes
 */
public interface NavButtonsVisibilityListener {

    /**
     * Called when the top-left nav buttons have been turned on or off
     * @param visible True if buttons visible
     */
    void onNavButtonsVisible(boolean visible);
}
