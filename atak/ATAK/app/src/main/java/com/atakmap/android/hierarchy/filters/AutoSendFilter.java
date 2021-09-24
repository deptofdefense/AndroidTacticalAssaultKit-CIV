
package com.atakmap.android.hierarchy.filters;

import com.atakmap.android.cotdetails.CoTAutoBroadcaster;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;

/**
 * Filter for showing all items that have "Auto-Send" currently enabled
 */
public class AutoSendFilter extends MapItemFilter {

    private final CoTAutoBroadcaster cab;

    public AutoSendFilter(HierarchyListItem.Sort sort) {
        super(sort);
        this.cab = CoTAutoBroadcaster.getInstance();
    }

    @Override
    public boolean accept(MapItem item) {
        return item instanceof Marker && this.cab.isBroadcast((Marker) item);
    }
}
