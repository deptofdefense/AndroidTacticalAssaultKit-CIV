
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

class TrackDetailHandler extends CotDetailHandler {
    private static final String TAG = "TrackDetailHandler";

    /**
     * Above this speed, display heading arrow
     */
    private final static double MIN_SPEED_MS = 1.34112; //3 MPH;

    TrackDetailHandler() {
        super("track");
    }

    @Override
    public boolean isSupported(MapItem item, CotEvent event, CotDetail detail) {
        return item instanceof Marker;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        // Only exported with self SA
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        Marker marker = (Marker) item;
        String headingS = detail.getAttribute("course");
        String speedS = detail.getAttribute("speed");

        try {
            double heading = Double.NaN;
            if (!FileSystemUtils.isEmpty(headingS)) {
                heading = Double.parseDouble(headingS);
                if (Math.abs(heading) > 3600)
                    heading = Double.NaN;
            }
            double speed = 0;
            if (!FileSystemUtils.isEmpty(speedS)) {
                speed = Double.parseDouble(speedS);
            }

            if (!Double.isNaN(heading) && !Double.isNaN(speed)
                    && speed > MIN_SPEED_MS) {
                //set heading style
                marker.setStyle(marker.getStyle()
                        | Marker.STYLE_ROTATE_HEADING_MASK);
            } else {
                //clear heading style
                marker.setStyle(marker.getStyle()
                        & ~Marker.STYLE_ROTATE_HEADING_MASK);
            }
            marker.setTrack(heading, speed);
            return ImportResult.SUCCESS;
        } catch (Exception ex) {
            Log.w(TAG, "Failed to parse marker track: " + marker.getUID(), ex);
        }

        //clear data
        marker.setTrack(Double.NaN, 0);
        marker.setStyle(marker.getStyle()
                & ~Marker.STYLE_ROTATE_HEADING_MASK);
        return ImportResult.FAILURE;
    }
}
