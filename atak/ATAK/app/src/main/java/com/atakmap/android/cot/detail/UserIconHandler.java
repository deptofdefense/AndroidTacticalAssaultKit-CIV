
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * User icon markers
 */
class UserIconHandler extends CotDetailHandler {

    UserIconHandler() {
        super("usericon");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        if (item.hasMetaValue(UserIcon.IconsetPath)) {
            String iconsetpath = item.getMetaString(UserIcon.IconsetPath, "");
            if (!FileSystemUtils.isEmpty(iconsetpath)) {
                CotDetail usericon = new CotDetail("usericon");
                usericon.setAttribute("iconsetpath", iconsetpath);
                detail.addChild(usericon);
                return true;
            }
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String path = detail.getAttribute("iconsetpath");
        if (!FileSystemUtils.isEmpty(path)) {
            item.setMetaString(UserIcon.IconsetPath, path);
            return ImportResult.SUCCESS;
        }
        return ImportResult.FAILURE;
    }
}
