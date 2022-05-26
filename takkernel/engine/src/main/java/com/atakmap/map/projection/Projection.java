
package com.atakmap.map.projection;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.math.PointD;

import gov.tak.api.annotation.DontObfuscate;

/**
 * Transforms a point in the geodetic coordinate space into a projected coordinate space.
 */
@DontObfuscate
public interface Projection {

    /**
     * Tranforms the specified geodetic coordinate into the projected coordinate space.
     * 
     * @param g A geodetic coordinate
     * @param p Returns the corresponding point in the projected coordinate space. If
     *            <code>null</code>, a new instance will be allocated to be returned.
     * @return The corresponding point in the projected coordinate space.
     */
    public PointD forward(GeoPoint g, PointD p);

    /**
     * Transforms the specified point in the projected coordinate space to into a geodetic
     * coordinate.
     * 
     * @param p A coordinate in the projected coordinate space
     * @param g Returns the geodetic coordinate. If <code>null</code> or READ_ONLY, a new 
     *          instance will be allocated to be returned.
     * @return The correspoinding geodetic coordinate.
     */
    public GeoPoint inverse(PointD p, GeoPoint g);

    /**
     * Returns the minimum latitude supported by the projection.
     * 
     * @return The minimum latitude supported by the projection.
     */
    public double getMinLatitude();

    /**
     * Returns the minimum longitude supported by the projection.
     * 
     * @return The minimum longitude supported by the projection.
     */
    public double getMinLongitude();

    /**
     * Returns the maximum latitude supported by the projection.
     * 
     * @return The maximum latitude supported by the projection.
     */
    public double getMaxLatitude();

    /**
     * Returns the maximum longitude supported by the projection.
     * 
     * @return The maximum longitude supported by the projection.
     */
    public double getMaxLongitude();

    /**
     * Returns the EPSG code for this projection.
     * 
     * @return The EPSG code for this projection.
     */
    public int getSpatialReferenceID();
    
    public boolean is3D();
}
