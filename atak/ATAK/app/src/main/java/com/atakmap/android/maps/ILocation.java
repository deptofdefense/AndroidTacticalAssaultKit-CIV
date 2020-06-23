
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

/**
 * Interface for an item that has a defined location and boundaries
 */
public interface ILocation {

    /**
     * Get the geo point of this item
     * For shapes this is usually the center point.   Please note, this call does not retrieve
     * the metadata for a specific item, just the basic GeoPoint.
     *
     * @param point Mutable point (null to create new point)
     * @return Geo point or null if N/A.   This lacks the associated metadata.
     */
    GeoPoint getPoint(GeoPoint point);

    /**
     * Get the geo bounds of this item
     * For points this is usually a bounds representation of the point
     *
     * @param bounds Mutable bounds (null to create new bounds)
     * @return Geo bounds or null if N/A
     */
    GeoBounds getBounds(MutableGeoBounds bounds);
}
