
package com.atakmap.android.geofence.data;

import com.atakmap.android.importexport.AbstractCotEventMarshal;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

/**
 *
 */
@Deprecated
public class GeoFenceCotEventMarshal extends AbstractCotEventMarshal {

    private static final String TAG = "GeoFenceCotEventMarshal";
    static final String CONTENT_TYPE = "Geo Fences";

    public GeoFenceCotEventMarshal() {
        super(CONTENT_TYPE);
    }

    @Override
    protected boolean accept(CotEvent event) {
        if (event.getType().startsWith(GeoFence.COT_TYPE)) {
            //TODO remove me after logging
            Log.d(TAG, "Accepting CotEvent: " + event.getType());
            return true;
        }

        return false;
    }

    @Override
    public int getPriorityLevel() {
        return 2;
    }
}
