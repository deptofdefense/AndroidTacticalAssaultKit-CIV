
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Detail handler for marker label attributes (not the content itself)
 */
class LabelDetailHandler extends CotDetailHandler {

    private static final String TAG = "LabelDetailHandler";

    LabelDetailHandler() {
        super(new HashSet<>(Arrays.asList("hideLabel", "labels_on")));
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        if (item instanceof Marker) {
            if (item.hasMetaValue("hideLabel")) {
                detail.addChild(new CotDetail("hideLabel"));
                return true;
            }
        } else {
            CotDetail d = new CotDetail("labels_on");
            d.setAttribute("value", String.valueOf(
                    item.hasMetaValue("labels_on")));
            detail.addChild(d);
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        boolean on = item instanceof Marker; // Marker labels are on by default
        if (detail.getElementName().equals("labels_on")) {
            String value = detail.getAttribute("value");
            on = value == null || Boolean.parseBoolean(value);
        } else if (detail.getElementName().equals("hideLabel"))
            on = false;
        if (item instanceof Marker)
            ((Marker) item).setShowLabel(on);
        else
            item.toggleMetaData("labels_on", on);
        return ImportResult.SUCCESS;
    }
}
