
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Device status
 */
class StatusDetailHandler extends CotDetailHandler {

    StatusDetailHandler() {
        super("status");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        // Only exported with self SA
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String readiness = detail.getAttribute("readiness");
        if (readiness != null)
            item.setMetaBoolean("readiness", Boolean.parseBoolean(readiness));

        String battery = detail.getAttribute("battery");
        if (battery != null)
            item.setMetaLong("battery", parseInt(battery, 100));

        return ImportResult.SUCCESS;
    }
}
