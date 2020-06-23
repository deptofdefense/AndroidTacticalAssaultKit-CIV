
package com.atakmap.android.cot.detail;

import android.graphics.Color;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Map item color
 *
 * XXX - Unfortunately not consistent whether the color is stored within
 * attribute "argb" or "value" - for now just include both
 */
class ColorDetailHandler extends CotDetailHandler {

    ColorDetailHandler() {
        super("color");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        if (item.hasMetaValue("color")) {
            CotDetail color = new CotDetail("color");
            String colorS = String.valueOf(item.getMetaInteger("color",
                    Color.WHITE));
            if (item instanceof Marker)
                color.setAttribute("argb", colorS);
            else
                color.setAttribute("value", colorS);
            detail.addChild(color);
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        //now pull out color
        String colorS = detail.getAttribute("argb");
        if (FileSystemUtils.isEmpty(colorS))
            colorS = detail.getAttribute("value");
        if (FileSystemUtils.isEmpty(colorS))
            return ImportResult.FAILURE;
        item.setMetaInteger("color", parseInt(colorS, Color.WHITE));
        return ImportResult.SUCCESS;
    }
}
