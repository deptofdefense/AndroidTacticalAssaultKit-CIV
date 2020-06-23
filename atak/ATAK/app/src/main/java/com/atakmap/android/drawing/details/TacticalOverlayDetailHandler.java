
package com.atakmap.android.drawing.details;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

public class TacticalOverlayDetailHandler extends CotDetailHandler {

    public TacticalOverlayDetailHandler() {
        super("tog");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event,
            CotDetail detail) {
        if (item instanceof Rectangle) {
            CotDetail cd = new CotDetail("tog");
            cd.setAttribute("enabled",
                    ((Rectangle) item).showTacticalOverlay() ? "1" : "0");
            detail.addChild(cd);
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        if (!(item instanceof Rectangle))
            return ImportResult.IGNORE;

        final String val = detail.getAttribute("enabled");
        if (val != null)
            ((Rectangle) item).showTacticalOverlay(val.equals("1"));

        return ImportResult.SUCCESS;
    }
}
