
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import java.util.Map;
import java.util.HashMap;

/**
 * Key for storing generic item metadata
 */
public class MetaDetailHandler extends CotDetailHandler {

    MetaDetailHandler() {
        super("meta");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        CotDetail meta = new CotDetail("meta");
        Map<String, Object> m = item.getMetaMap("meta");
        if (m != null && !m.isEmpty()) {
            for (Map.Entry<String, Object> pair : m.entrySet()) {
                String k = pair.getKey();
                String v = (String) pair.getValue();
                CotDetail entry = new CotDetail();
                entry.setAttribute("key", k);
                entry.setAttribute("value", v);
                meta.addChild(entry);
            }
            detail.addChild(meta);
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        if (item == null)
            return ImportResult.FAILURE;

        Map<String, Object> m = new HashMap<>();

        int len = detail.childCount();
        for (int i = 0; i < len; ++i) {
            CotDetail cd = detail.getChild(i);
            String key = cd.getAttribute("key");
            String value = cd.getAttribute("value");
            if (key != null && value != null)
                m.put(key, value);

        }
        if (!m.isEmpty())
            item.setMetaMap("meta", m);
        else
            item.removeMetaData("meta");
        return ImportResult.SUCCESS;
    }
}
