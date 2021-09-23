
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * CoT detail for map item height in meters
 *
 * Format: <height value="12.0"/>
 */
class HeightDetailHandler extends CotDetailHandler {

    private static final String TAG = "HeightDetailHandler";

    HeightDetailHandler() {
        super("height");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        double height = item.getHeight();
        if (Double.isNaN(height))
            return false;

        CotDetail d = new CotDetail("height");
        d.setAttribute("value", String.valueOf(height));
        detail.addChild(d);
        return true;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String value = detail.getAttribute("value");
        if (FileSystemUtils.isEmpty(value))
            value = detail.getInnerText(); // Legacy
        double height = parseDouble(value, Double.NaN);
        item.setHeight(height);
        return ImportResult.SUCCESS;
    }
}
