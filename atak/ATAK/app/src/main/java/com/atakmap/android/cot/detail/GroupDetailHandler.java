
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Team markers - group
 */
class GroupDetailHandler extends CotDetailHandler {

    GroupDetailHandler() {
        super("__group");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        // Only exported with self SA
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String group = detail.getAttribute("name");
        if (!FileSystemUtils.isEmpty(group))
            item.setMetaString("team", group);

        String role = detail.getAttribute("role");
        if (!FileSystemUtils.isEmpty(role))
            item.setMetaString("atakRoleType", role);

        return ImportResult.SUCCESS;
    }
}
