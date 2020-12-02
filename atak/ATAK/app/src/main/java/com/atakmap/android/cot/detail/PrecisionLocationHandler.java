
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.HashMap;
import java.util.Map;

/**
 * CoT detail handler for the <precisionlocation/> tag
 */
public class PrecisionLocationHandler extends CotDetailHandler {

    private static PrecisionLocationHandler _instance;

    public static final String PRECISIONLOCATION = "precisionlocation";

    private static final Map<String, Class<?>> MD_KEYS = new HashMap<>();
    static {
        MD_KEYS.put(GeoPointMetaData.GEOPOINT_SOURCE, String.class);
        MD_KEYS.put(GeoPointMetaData.ALTITUDE_SOURCE, String.class);
        MD_KEYS.put(GeoPointMetaData.PRECISE_IMAGE_FILE, String.class);
        MD_KEYS.put(GeoPointMetaData.PRECISE_IMAGE_FILE_X, Double.class);
        MD_KEYS.put(GeoPointMetaData.PRECISE_IMAGE_FILE_Y, Double.class);
    }

    PrecisionLocationHandler() {
        super(PRECISIONLOCATION);
        _instance = this;
    }

    // for usage by our self position reporting //
    public static PrecisionLocationHandler getInstance() {
        return _instance;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        GeoPointMetaData gpmd = getPoint(item);
        CotDetail d = toPrecisionLocation(gpmd);
        if (d != null)
            detail.addChild(d);
        return true;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        GeoPointMetaData gpmd = getPrecisionLocation(event);

        PointMapItem pmi = null;
        if (item instanceof PointMapItem)
            pmi = (PointMapItem) item;
        else if (item instanceof AnchoredMapItem)
            pmi = ((AnchoredMapItem) item).getAnchorItem();
        if (pmi != null)
            pmi.setPoint(gpmd);
        else {
            for (String k : gpmd.getMetaData().keySet())
                item.removeMetaData(k);
            item.copyMetaData(gpmd.getMetaData());
        }
        return ImportResult.SUCCESS;
    }

    /**
     * Get point metadata from a detail
     *
     * @param event CoT event
     * @return Geo point metadata
     */
    public static GeoPointMetaData getPrecisionLocation(final CotEvent event) {
        GeoPoint gp = event.getGeoPoint();
        if (gp == null)
            gp = GeoPoint.ZERO_POINT;

        CotDetail detail = event.findDetail(PRECISIONLOCATION);
        if (detail == null)
            return GeoPointMetaData.wrap(gp);

        GeoPointMetaData gpm = new GeoPointMetaData();
        gpm.set(gp);

        for (Map.Entry<String, Class<?>> e : MD_KEYS.entrySet()) {
            String k = e.getKey();
            String v = detail.getAttribute(k);
            Class<?> c = e.getValue();
            if (v != null) {
                if (c == Double.class) {
                    try {
                        gpm.setMetaValue(k, Double.parseDouble(v));
                    } catch (Exception ignore) {
                    }
                } else
                    gpm.setMetaValue(k, v);
            }
        }

        return gpm;
    }

    /**
     * Convert a point's metadata to a CoT detail
     *
     * @param gpmd Geo point metadata
     * @return CoT detail or null if no applicable metadata found
     */
    public static CotDetail toPrecisionLocation(GeoPointMetaData gpmd) {
        if (gpmd == null)
            return null;
        CotDetail ret = null;
        for (String k : MD_KEYS.keySet()) {
            Object v = gpmd.getMetaData(k);
            if (v == null || v instanceof Double && Double.isNaN((Double) v))
                continue;
            if (ret == null)
                ret = new CotDetail(PRECISIONLOCATION);
            ret.setAttribute(k, String.valueOf(v));
        }
        return ret;
    }
}
