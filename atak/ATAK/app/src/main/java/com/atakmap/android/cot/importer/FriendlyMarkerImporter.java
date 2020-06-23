
package com.atakmap.android.cot.importer;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

/**
 * Importer for friendly markers
 * Includes extra logic to deal with contact friendlies
 */
public class FriendlyMarkerImporter extends MarkerImporter {

    private static final double ZORDER_CONTACTS = -1000d;

    public FriendlyMarkerImporter(MapView mapView, MapGroup group) {
        super(mapView, group, "a-f", true);
    }

    @Override
    protected void addToGroup(MapItem item) {
        super.addToGroup(item);
        if (item.hasMetaValue("endpoint"))
            item.setZOrder(ZORDER_CONTACTS);
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        if (item.hasMetaValue("team"))
            return R.drawable.team_human;
        return R.drawable.friendly;
    }
}
