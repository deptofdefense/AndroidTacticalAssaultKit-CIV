
package com.atakmap.android.importexport;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

public class CotEventFactory {
    private static final String TAG = "CotEventFactory";

    private CotEventFactory() {
    }

    /**
     * Given a MapItem, produce a valid CotEvent first by trying to 
     * convert it using a CotEvent service provider, then if the 
     * event has a "type" and does not contain the value "nevercot",
     * then try to create a GENERIC cot message.
     */
    public static CotEvent createCotEvent(final MapItem item) {

        // the item has been marked so that it will never be turned into CoT
        if (item == null || item.hasMetaValue("nevercot"))
            return null;

        // Simple and proper way to convert a map item to a CoT event
        if (item instanceof Exportable) {
            Exportable export = (Exportable) item;
            try {
                if (export.isSupported(CotEvent.class))
                    return (CotEvent) export.toObjectOf(CotEvent.class, null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to export item to CoT: " + item, e);
            }
        }

        // Old deprecated marker CoT event implementation
        if (item instanceof Marker && item.hasMetaValue("type")) {
            try {
                return createDefaultEvent((Marker) item);
            } catch (Exception e) {
                Log.d(TAG, "error occurred", e);
                return null;
            }
        }

        return null;
    }

    /* Deprecated functionality */

    /**
     * XXX: This method that should be broken apart to use the SPI system. 
     *
     * DO NOT ADD TO IT.
     *
     * @deprecated
     **/
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = false)
    private static CotEvent createDefaultEvent(final Marker marker) {
        CotEvent cotEvent = new CotEvent();

        GeoPoint point = marker.getPoint();

        cotEvent.setPoint(new CotPoint(point));
        cotEvent.setType(marker.getType());
        cotEvent.setUID(marker.getUID());
        String how = marker.getMetaString("how", null);
        if (how != null) {
            cotEvent.setHow(how);
        } else {
            // if how is missing, then the conversion to a marker will cause an invalid 
            // CotEvent
            Log.d(TAG, "missing how type, filling in with h-e");
            cotEvent.setHow("h-e");
        }
        String opex = marker.getMetaString("opex", null);
        if (opex != null)
            cotEvent.setOpex(opex);
        String qos = marker.getMetaString("qos", null);
        if (qos != null)
            cotEvent.setQos(qos);
        String access = marker.getMetaString("access", null);
        if (access != null)
            cotEvent.setAccess(access);

        cotEvent.setVersion("2.0");

        // per Josh Sterling as part of the COVID 19 response bump the stale time 
        // for markers from 300 (5 minutes) to 1 year
        int staleSeconds = 31536000;
        if (marker.hasMetaValue("cotDefaultStaleSeconds")) {
            staleSeconds = marker.getMetaInteger("cotDefaultStaleSeconds",
                    staleSeconds);
        }

        CoordinatedTime time = new CoordinatedTime();
        cotEvent.setTime(time);
        cotEvent.setStart(time);
        cotEvent.setStale(time.addSeconds(staleSeconds));

        CotDetail detail = new CotDetail("detail");
        cotEvent.setDetail(detail);

        if (marker.hasMetaValue("initialBearing")) {
            CotDetail heading = new CotDetail("initialBearing");
            heading.setAttribute("value",
                    Double.toString(marker
                            .getMetaDouble("initialBearing", 0.0d)));

            detail.addChild(heading);
        }

        if (marker.hasMetaValue("readiness")) {
            CotDetail status = new CotDetail("status");
            status.setAttribute("readiness",
                    String.valueOf(marker.getMetaBoolean("readiness", false)));
            detail.addChild(status);
        }

        if (marker.hasMetaValue("archive")) {
            CotDetail archive = new CotDetail("archive");
            detail.addChild(archive);
        }

        CotDetailManager.getInstance().addDetails(marker, cotEvent);

        return cotEvent;
    }

}
