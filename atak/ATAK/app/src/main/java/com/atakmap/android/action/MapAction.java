
package com.atakmap.android.action;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;

/**
 * An invocation point for a sequence of commands to take place in the engine
 */
public interface MapAction {

    /**
     * Callback for when an action occurs for a provided map view and map item.
     * @param mapView the mapView that the action should be performed on
     * @param mapItem the map item that the action should be performed on
     */
    void performAction(MapView mapView, MapItem mapItem);
}
