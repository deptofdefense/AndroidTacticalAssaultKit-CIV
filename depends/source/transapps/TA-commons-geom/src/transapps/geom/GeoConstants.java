// Created by plusminus on 17:27:54 - 30.09.2008
package transapps.geom;

/**
 *
 * Some basic Geo constants such as Radius of Earth and conversion factors like
 * meters per nautical mile.
 *
 * @author Nicolas Gramlich
 * 
 */
public interface GeoConstants {
    // ===========================================================
    // Final Fields
    // ===========================================================

    public static final int RADIUS_EARTH_METERS = 6378137; // http://en.wikipedia.org/wiki/Earth_radius#Equatorial_radius
    public static final double METERS_PER_STATUTE_MILE = 1609.344; // http://en.wikipedia.org/wiki/Mile
    public static final double METERS_PER_NAUTICAL_MILE = 1852; // http://en.wikipedia.org/wiki/Nautical_mile
    public static final double FEET_PER_METER = 3.2808399; // http://en.wikipedia.org/wiki/Feet_%28unit_of_length%29
    public static final int EQUATORCIRCUMFENCE = (int) (2 * Math.PI * RADIUS_EARTH_METERS);

    public static final float DEG2RAD = (float) (Math.PI / 180.0);
    public static final float RAD2DEG = (float) (180.0 / Math.PI);

    public static final float PI = (float) Math.PI;
    public static final float PI_2 = PI / 2.0f;
    public static final float PI_4 = PI / 4.0f;

    // ===========================================================
    // Methods
    // ===========================================================
}
