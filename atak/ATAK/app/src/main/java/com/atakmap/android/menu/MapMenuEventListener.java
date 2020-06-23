
package com.atakmap.android.menu;

import com.atakmap.android.maps.MapItem;

/**
 * Event interface for radial menu events
 */
public interface MapMenuEventListener {

    /**
     * The radial menu is about to be shown for an item
     * @param item Map item
     * @return True if event handled (suppress default behavior)
     */
    boolean onShowMenu(MapItem item);

    /**
     * The radial menu is about to be closed
     */
    void onHideMenu(MapItem item);
}
