package gov.tak.api.engine.map.coords;

import gov.tak.api.engine.math.PointD;

/**
 * Transforms a point in the geodetic coordinate space into a projected coordinate space.
 */
public interface IProjection {

    /**
     * Tranforms the specified geodetic coordinate into the projected coordinate space.
     *
     * @param g A geodetic coordinate
     * @param p Returns the corresponding point in the projected coordinate space.
     * @return  <code>true</code> if the input was successfully transformed, <code>false</code>
     *          otherwise
     */
    boolean forward(GeoPoint g, PointD p);

    /**
     * Transforms the specified point in the projected coordinate space to into a geodetic
     * coordinate.
     *
     * @param p A coordinate in the projected coordinate space
     * @param g Returns the geodetic coordinate.
     * @return  <code>true</code> if the input was successfully transformed, <code>false</code>
     *          otherwise
     */
    boolean inverse(PointD p, GeoPoint g);

    /**
     * Returns the EPSG code for this projection.
     *
     * @return The EPSG code for this projection.
     */
    int getSpatialReferenceID();
}