
package com.atakmap.android.icons;

import com.atakmap.android.maps.Marker;

/**
 * 
 * 
 */
public interface IconAdapter {

    /**
     * Return true if the icon was adapter for the specified marker
     * 
     * @param marker the marker in which to adapt
     * @return true if the marker was adaptered.
     */
    boolean adapt(Marker marker);

    void dispose();
}
