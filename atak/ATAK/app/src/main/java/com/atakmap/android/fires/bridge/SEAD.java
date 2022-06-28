
package com.atakmap.android.fires.bridge;

import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Bridge component in support of the mobileJECL plugin which makes use of the call toCSV.
 */
public abstract class SEAD {
    public static SEAD impl;

    /**
     * Used by the system plugin to register a concrete implementation of the
     * call for fire capability.
     * @param concreteImpl the concrete call for fire implementation
     */
    public static void registerImplementation(SEAD concreteImpl) {
        if (impl == null)
            impl = concreteImpl;
    }

    public synchronized static String toCSV(GeoPoint p, MapItem item) {
        impl.set_location(p.getLatitude() + " " + p.getLongitude(),
                CoordinateFormatUtilities.formatToString(p,
                        CoordinateFormat.MGRS));
        impl.set_markerUID(item.getUID());
        return impl.toCSV();
    }

    public abstract void set_location(String location, String mgrsLocation);

    public abstract void set_markerUID(String uid);

    public abstract String toCSV();

}
