
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Interface for an item to use as a reference location
 */
public interface IReferenceLocation {

    /**
     * Get the reference geo point of this item
     *
     * @param point Mutable point
     * @return Geo point or null if N/A.   This lacks the associated metadata.
     */
    GeoPoint getReferencePoint(GeoPoint point);

    /**
     * Get the title of the reference geo point
     *
     * @return Title or null if N/A
     */
    String getReferenceTitle();
}
