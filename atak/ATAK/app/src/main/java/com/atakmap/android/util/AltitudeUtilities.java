
package com.atakmap.android.util;

import android.content.SharedPreferences;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.UnitPreferences;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * 
 */
public class AltitudeUtilities {

    private static final String TAG = "AltitudeUtilities";

    /**
     * Given a point, produce the standard altitude format based on the current preferences in the
     * system.
     * @param pointMD the geopoint with metadata.
     * @return the standard formatting for the altitude.
     */
    public static String format(GeoPointMetaData pointMD) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return "---";

        return new UnitPreferences(mv).formatAltitude(pointMD);
    }

    /**
     * Given just the geopoint without metadata, apply the formatting for the altitude based on the
     * current shared preferences.
     * @param point the geopoint
     * @param prefs the system shared preferences
     * @return the standard format for the altitude based on the current preferences.
     */
    public static String format(GeoPoint point, SharedPreferences prefs) {
        return format(point, prefs, false);
    }

    /**
     * Check the "Display AGL" preference and format relative to ground level if ground elevation
     * data can be loaded. If not found, display "--ft AGL"  If AGL option is not set, then fall
     * back on MSL or HAE
     *
     * Note, see MapCoreInterfaces/EGM96 for formatting, given a ground reference
     * This method uses the ATAK/Dt2ElevationModel to query the DEM and is part of
     * the ATAK project b/c the ATAK SDK is not delivered with DTED data
    
     * @param point the geopoint to be formatted
     * @param prefs the system preferences
     * @return if padding is desired.
     */
    public static String format(GeoPoint point, SharedPreferences prefs,
            boolean pad) {

        MapView mv = MapView.getMapView();
        if (mv == null)
            return "---";

        String altString = new UnitPreferences(mv).formatAltitude(point);

        if (pad) {
            if (altString.length() == 9)
                altString = "    " + altString;
            else if (altString.length() == 10)
                altString = "   " + altString;
            else if (altString.length() == 11)
                altString = "  " + altString;
            else if (altString.length() == 12)
                altString = " " + altString;
        }

        return altString;
    }
}
