
package com.atakmap.android.lrf;

public interface RangeFinderAction {

    /**
     * A range finder action is defined optimally as a D(istance),A(zimuth), and E(levation).
     * In some cases, the hardware in not equiped to push out an Azimuth.   Appropriate care
     * should be taken to account for azimuth as Double.NaN.   Some possible actions would be 
     * to toast the user and pull the azimuth off the phone.
     */
    void onRangeFinderInfo(String uidPrefix, final double distance,
            final double azimuth, final double zAngle);
}
