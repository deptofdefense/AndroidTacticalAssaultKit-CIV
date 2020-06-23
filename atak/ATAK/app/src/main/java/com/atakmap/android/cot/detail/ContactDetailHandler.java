
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Processes the <contact callsign=""/> detail tag.
 */
class ContactDetailHandler extends CotDetailHandler {

    ContactDetailHandler() {
        super("contact");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        String title = item.getTitle();
        if (item instanceof Marker) {
            // For markers the "callsign" attributes takes priority
            String callsign = item.getMetaString("callsign", null);
            if (!FileSystemUtils.isEmpty(callsign))
                title = callsign;
        }
        if (!FileSystemUtils.isEmpty(title)) {
            CotDetail contact = new CotDetail("contact");
            contact.setAttribute("callsign", title);
            detail.addChild(contact);
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String callsign = detail.getAttribute("callsign");
        if (FileSystemUtils.isEmpty(callsign))
            return ImportResult.IGNORE;
        if (item instanceof Marker) {
            item.setMetaString("callsign", callsign);
            PlacePointTool.updateCallsign((Marker) item);
        } else
            item.setTitle(callsign);
        return ImportResult.SUCCESS;
    }
}
