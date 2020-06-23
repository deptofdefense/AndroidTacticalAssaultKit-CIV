
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Responsible for setting a marker to be archived if the archive tag is encountered.
 * <archive/>
 */
class ArchiveDetailHandler extends CotDetailHandler {

    ArchiveDetailHandler() {
        super("archive");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        if (item.hasMetaValue("archive")) {
            detail.addChild(new CotDetail("archive"));
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        item.setMetaBoolean("archive", true);
        return ImportResult.SUCCESS;
    }
}
