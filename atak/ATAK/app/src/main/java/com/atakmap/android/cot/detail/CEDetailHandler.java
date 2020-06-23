
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Responsible for handling the <ce_human_input/> tag.   Not sure if this tag is in use anymore.
 */
class CEDetailHandler extends CotDetailHandler {

    private static final String TAG = "CEDetailHandler";
    private static final String META_STRING = "ce_human_input";

    CEDetailHandler() {
        super("ce_human_input");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        if (item.hasMetaValue(META_STRING)) {
            CotDetail cd = new CotDetail(META_STRING);
            cd.setInnerText(Boolean.toString(item.getMetaBoolean(
                    META_STRING, false)));
            detail.addChild(cd);
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        if (item == null)
            return ImportResult.FAILURE;
        String value = detail.getInnerText();
        if (value != null && !value.equals(""))
            item.setMetaBoolean(META_STRING, value.equals("true"));
        return ImportResult.SUCCESS;
    }
}
