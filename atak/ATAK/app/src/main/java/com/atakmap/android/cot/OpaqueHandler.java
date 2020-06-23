
package com.atakmap.android.cot;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.Map;
import java.util.HashMap;

/**
 * TODO: Why is this a singleton?
 */
public class OpaqueHandler implements MarkerDetailHandler {

    static private OpaqueHandler _this;

    private OpaqueHandler() {
    }

    synchronized public static OpaqueHandler getInstance() {
        if (_this == null)
            _this = new OpaqueHandler();
        return _this;
    }

    @Override
    public void toCotDetail(final Marker marker, final CotDetail detail) {
        toCotDetail((MapItem) marker, detail);
    }

    public void toCotDetail(final MapItem marker, final CotDetail detail) {
        if (marker == null || !marker.hasMetaValue("opaque-details"))
            return;
        synchronized (this) {
            Map<String, Object> opaqueDetails = marker
                    .getMetaMap("opaque-details");
            if (opaqueDetails == null)
                opaqueDetails = new HashMap<>();

            for (Map.Entry<String, Object> entry : opaqueDetails.entrySet()) {
                Object v = entry.getValue();
                if (v instanceof CotDetail) {
                    String name = entry.getKey();
                    CotDetail existing = detail.getFirstChildByName(0, name);
                    if (existing == null)
                        detail.addChild((CotDetail) v);
                }
            }
        }
    }

    @Override
    public void toMarkerMetadata(Marker marker, CotEvent event,
            CotDetail detail) {
        toMarkerMetadata((MapItem) marker, event, detail);
    }

    public void toMarkerMetadata(MapItem marker, CotEvent event,
            CotDetail detail) {

        synchronized (this) {
            Map<String, Object> opaqueDetails = marker
                    .getMetaMap("opaque-details");
            if (opaqueDetails == null)
                opaqueDetails = new HashMap<>();

            opaqueDetails.put(detail.getElementName(), detail);
            marker.setMetaMap("opaque-details", opaqueDetails);
        }
    }
}
