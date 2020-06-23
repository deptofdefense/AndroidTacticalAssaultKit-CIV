
package com.atakmap.android.cot.importer;

import android.os.Bundle;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Importer for CASEVAC markers
 */
public class CASEVACMarkerImporter extends MarkerImporter {

    private static final String COT_TYPE = "b-r-f-h-c";

    public CASEVACMarkerImporter(MapView mapView) {
        super(mapView, "CASEVAC", COT_TYPE, true);
    }

    @Override
    public ImportResult importData(CotEvent event, Bundle extras) {
        if (event.getType().startsWith("a-f-G")) {
            MapItem item = findItem(event);
            if (item instanceof Marker
                    && item.getMetaBoolean("casevac", false))
                item.setType(COT_TYPE);
        }
        return super.importData(event, extras);
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.damaged;
    }
}
