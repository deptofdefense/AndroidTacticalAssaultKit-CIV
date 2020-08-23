
package com.atakmap.android.vehicle;

import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;

/**
 * Common interface for the 3 vehicle types
 */
public interface VehicleMapItem extends AnchoredMapItem {

    /**
     * Get the width (wingspan) of the vehicle
     * @return Width in meters
     */
    double getWidth();

    /**
     * Get the length (nose to rear) of the vehicle
     * @return Length in meters
     */
    double getLength();

    /**
     * Get the height of the vehicle
     * @return Height in meters
     */
    double getHeight();

    /**
     * Get the center point of this vehicle
     * @return Center point w/ metadata
     */
    GeoPointMetaData getCenter();

    /**
     * Get the vehicle's current azimuth
     *
     * XXX - Maybe someday we can bump to Java 8 so we can use default method
     * impls here instead of copy-pasting degree conversion code ad nauseam...
     *
     * @param ref Desired north reference
     * @return Azimuth in degrees
     */
    double getAzimuth(NorthReference ref);

    /**
     * Set the azimuth/heading of this vehicle model
     * @param deg Degrees
     * @param ref Reference the degrees are in
     */
    void setAzimuth(double deg, NorthReference ref);
}
