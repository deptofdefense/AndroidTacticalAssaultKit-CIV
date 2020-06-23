// Created by plusminus on 20:36:01 - 26.09.2008
package transapps.mapi.util;

import transapps.geom.GeoConstants;


/**
 * 
 * @author Nicolas Gramlich
 * 
 */
public class MyMath {
    // ===========================================================
    // Constants
    // ===========================================================

    public static final double LN2 = Math.log(2);
    
    // ===========================================================
    // Fields
    // ===========================================================

    // ===========================================================
    // Constructors
    // ===========================================================

    /**
     * This is a utility class with only static members.
     */
    private MyMath() {
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

    /**
     * Calculates i.e. the increase of zoomlevel needed when the visible latitude needs to be bigger
     * by <code>factor</code>.
     * 
     * Assert.assertEquals(1, getNextSquareNumberAbove(1.1f)); Assert.assertEquals(2,
     * getNextSquareNumberAbove(2.1f)); Assert.assertEquals(2, getNextSquareNumberAbove(3.9f));
     * Assert.assertEquals(3, getNextSquareNumberAbove(4.1f)); Assert.assertEquals(3,
     * getNextSquareNumberAbove(7.9f)); Assert.assertEquals(4, getNextSquareNumberAbove(8.1f));
     * Assert.assertEquals(5, getNextSquareNumberAbove(16.1f));
     * 
     * Assert.assertEquals(-1, - getNextSquareNumberAbove(1 / 0.4f) + 1); Assert.assertEquals(-2, -
     * getNextSquareNumberAbove(1 / 0.24f) + 1);
     * 
     * @param factor
     * @return
     */
    public static int getNextSquareNumberAbove(final float factor) {
        int out = 0;
        int cur = 1;
        int i = 1;
        while (true) {
            if (cur > factor)
                return out;

            out = i;
            cur *= 2;
            i++;
        }
    }
    
    public static int getNextPowerOf2( int x ) {
        if (x < 0)
            return 0;
        --x;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return x+1;
    }
    
    public static double gudermannInverse(final double aLatitude) {
        return Math.log(Math.tan(GeoConstants.PI_4 + (GeoConstants.DEG2RAD * aLatitude / 2)));
    }

    public static double gudermann(final double y) {
        return GeoConstants.RAD2DEG * Math.atan(Math.sinh(y));
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
