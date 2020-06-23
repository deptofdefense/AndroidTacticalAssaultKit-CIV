
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Map item remarks string
 */
class RemarksDetailHandler extends CotDetailHandler {

    RemarksDetailHandler() {
        super("remarks");
    }

    @Override
    public boolean toCotDetail(MapItem marker, CotEvent event,
            CotDetail detail) {
        if (marker.hasMetaValue("remarks")) {
            CotDetail remarks = new CotDetail("remarks");
            remarks.setInnerText(marker.getMetaString("remarks", null));
            detail.addChild(remarks);
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem marker, CotEvent event,
            CotDetail detail) {
        String remarksText = detail.getInnerText();
        if (remarksText == null)
            remarksText = "";
        marker.setRemarks(remarksText);
        return ImportResult.SUCCESS;
    }
}
