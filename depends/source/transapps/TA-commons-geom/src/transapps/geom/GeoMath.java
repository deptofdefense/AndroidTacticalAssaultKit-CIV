// Created by plusminus on 20:36:01 - 26.09.2008
package transapps.geom;



/**
 *
 * Class with some utility Geo Math functions mostly related to map projections,
 * specifically the Mercator projection and mapping the distance in y up from the equator
 * of a latitude point in the Mercator flat map projection.
 *
 *
 * @author Nicolas Gramlich
 * 
 */
public class GeoMath implements GeoConstants {
    // ===========================================================
    // Constants
    // ===========================================================

    // ===========================================================
    // Fields
    // ===========================================================

    // ===========================================================
    // Constructors
    // ===========================================================

    /**
     * This is a utility class with only static members.
     */
    private GeoMath() {
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    // ===========================================================
    // Methods from SuperClass/Interfaces
    // ===========================================================

    // ===========================================================
    // Methods
    // ===========================================================

    public static double gudermannInverse(final double aLatitude) {
        return Math.log(Math.tan(PI_4 + (DEG2RAD * aLatitude / 2)));
    }

    public static double gudermann(final double y) {
        return RAD2DEG * Math.atan(Math.sinh(y));
    }

    public static int mod(int number, final int modulus) {
        if (number > 0)
            return number % modulus;

        while (number < 0)
            number += modulus;

        return number;
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
}
