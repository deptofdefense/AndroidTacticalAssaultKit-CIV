
package com.atakmap.android.cot;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Responsible for implementing any marker to detail conversions.
 * It's recommended to use {@link CotDetailHandler} instead, which contains
 * extra functionality and is compatible with all map items instead of
 * just markers
 */
public interface MarkerDetailHandler {

    /**
     * Given a CotEvent and CotDetails create the appropriate entries in a
     * Marker.
     */
    void toMarkerMetadata(Marker marker, CotEvent event, CotDetail detail);

    /**
     * Given a marker and an CotDetail (usually empty), fill the detail as 
     * appropriate from entries in the Marker Detail.
     */
    void toCotDetail(Marker marker, CotDetail detail);

}
