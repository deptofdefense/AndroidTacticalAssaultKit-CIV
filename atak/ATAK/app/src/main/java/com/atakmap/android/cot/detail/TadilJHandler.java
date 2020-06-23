
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Handles both "tadilj" and "jtids" tags
 */
class TadilJHandler extends CotDetailHandler {

    TadilJHandler() {
        super(new HashSet<>(Arrays.asList("tadilj", "__jtids")));
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        String value = item.getMetaString("tadilj", null);
        if (FileSystemUtils.isEmpty(value))
            return false;
        CotDetail tadilj = new CotDetail("tadilj");
        tadilj.setAttribute("tadilj", value);
        detail.addChild(tadilj);
        return true;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String tadilj = detail.getAttribute("jstn");
        if (FileSystemUtils.isEmpty(tadilj))
            tadilj = detail.getAttribute("tadilj");
        if (!FileSystemUtils.isEmpty(tadilj)) {
            item.setMetaString("tadilj", tadilj);
            return ImportResult.SUCCESS;
        }
        return ImportResult.FAILURE;
    }
}
