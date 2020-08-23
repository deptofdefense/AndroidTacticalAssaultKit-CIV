
package com.atakmap.android.routes.routearound;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Shape;

/** Listener that removes a region from the route around region view model if
 * the user removes it from the map. */
public class RegionRemovalListener
        implements MapEventDispatcher.MapEventDispatchListener {
    private final RouteAroundRegionViewModel vm;

    public RegionRemovalListener(RouteAroundRegionViewModel vm) {
        this.vm = vm;
    }

    @Override
    public void onMapEvent(MapEvent mapEvent) {
        if (mapEvent != null
                && mapEvent.getType().equals(MapEvent.ITEM_REMOVED)) {
            MapItem item = mapEvent.getItem();

            if (item instanceof Shape) {
                vm.removeRegion((Shape) item);
            }
        }
    }
}
