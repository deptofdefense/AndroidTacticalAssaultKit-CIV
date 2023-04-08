
package com.atakmap.android.icons;

import com.atakmap.android.maps.Marker;

import gov.tak.api.util.Disposable;

/**
 * 
 * 
 */
public interface IconAdapter extends Disposable {

    /**
     * Return true if the icon was adapter for the specified marker
     * 
     * @param marker the marker in which to adapt
     * @return true if the marker was adaptered.
     */
    boolean adapt(Marker marker);

}
