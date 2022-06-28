
package com.atakmap.android.preference;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.Area;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.elevation.ElevationManager;

/**
 * Unit preferences helper class
 */
public class UnitPreferences extends AtakPreferences {

    public static final String COORD_FMT = "coord_display_pref";
    public static final String RANGE_SYSTEM = "rab_rng_units_pref";
    public static final String BEARING_UNITS = "rab_brg_units_pref";
    public static final String NORTH_REFERENCE = "rab_north_ref_pref";
    public static final String FT_MILES_THRESH = "rng_feet_display_pref";
    public static final String M_KM_THRESH = "rng_meters_display_pref";
    public static final String AREA_SYSTEM = "area_display_pref";
    public static final String ALTITUDE_REFERENCE = "alt_display_pref";
    public static final String ALTITUDE_UNITS = "alt_unit_pref";

    public UnitPreferences(MapView mapView) {
        super(mapView);
    }

    /**
     * Get the preferred coordinate format
     * @return Coordinate format
     */
    public CoordinateFormat getCoordinateFormat() {
        return CoordinateFormat.find(get(COORD_FMT, _context.getString(
                R.string.coord_display_pref_default)));
    }

    public void setCoordinateFormat(CoordinateFormat format) {
        set(COORD_FMT, format.toString());
    }

    /**
     * Get the preferred range system
     * @return {@link Span#ENGLISH}, {@link Span#METRIC}, or {@link Span#NM}
     */
    public int getRangeSystem() {
        return get(RANGE_SYSTEM, Span.METRIC);
    }

    public void setRangeSystem(int rangeSys) {
        set(RANGE_SYSTEM, String.valueOf(rangeSys));
    }

    /**
     * Get preferred range units based on set thresholds
     * @param range Range value to use as reference (meters)
     * @return Range units
     */
    public Span getRangeUnits(double range) {
        int system = getRangeSystem();
        if (system == Span.METRIC) {
            int thresh = get(M_KM_THRESH, 2000);
            if (range > thresh)
                return Span.KILOMETER;
            return Span.METER;
        } else if (system == Span.ENGLISH) {
            range = SpanUtilities.convert(range, Span.METER, Span.FOOT);
            int thresh = get(FT_MILES_THRESH, 5280);
            if (range > thresh)
                return Span.MILE;
            return Span.FOOT;
        }
        return Span.NAUTICALMILE;
    }

    /**
     * Get the preferred area system
     * @return {@link Area#METRIC}, {@link Area#ENGLISH}, {@link Area#NM},
     * or {@link Area#AC}
     */
    public int getAreaSystem() {
        return get(AREA_SYSTEM, Area.METRIC);
    }

    public void setAreaSystem(int system) {
        set(AREA_SYSTEM, system);
    }

    /**
     * Get the preferred bearing units
     * @return Bearing units
     */
    public Angle getBearingUnits() {
        int angValue = get(BEARING_UNITS, Angle.DEGREE.getValue());
        Angle a = Angle.findFromValue(angValue);
        return a != null ? a : Angle.DEGREE;
    }

    public void setBearingUnits(Angle units) {
        set(BEARING_UNITS, String.valueOf(units.getValue()));
    }

    /**
     * Get the current altitude reference for display
     * @return Altitude reference (MSL, HAE, or AGL)
     */
    public String getAltitudeReference() {
        return get(ALTITUDE_REFERENCE, "MSL");
    }

    /**
     * Get the preferred altitude units
     * @return Preferred altitude span
     */
    public Span getAltitudeUnits() {
        int system = get(ALTITUDE_UNITS, Span.ENGLISH);
        return system == 0 ? Span.FOOT : Span.METER;
    }

    public void setAltitudeUnits(Span unit) {
        set(ALTITUDE_UNITS, String.valueOf(unit.getType()));
    }

    /**
     * Get the preferred north reference
     * @return North reference
     */
    public NorthReference getNorthReference() {
        int refValue = get(NORTH_REFERENCE, NorthReference.MAGNETIC
                .getValue());
        NorthReference ref = NorthReference.findFromValue(refValue);
        return ref != null ? ref : NorthReference.MAGNETIC;
    }

    /**
     * Set the north reference for the default units.
     * @param ref the reference to be used.
     */
    public void setNorthReference(NorthReference ref) {
        set(NORTH_REFERENCE, String.valueOf(ref.getValue()));
    }

    /**
     * Produce a human-readable coordinate string
     * @param point Point
     * @param includeAlt True to include altitude on a new line
     * @return Coordinate string
     */
    public String formatPoint(GeoPointMetaData point, boolean includeAlt) {
        String ret = CoordinateFormatUtilities.formatToString(point.get(),
                getCoordinateFormat());
        if (includeAlt)
            ret += "\n" + formatAltitude(point);
        return ret;
    }

    /**
     * Given a point, format the point using the default preference into a human readable format.
     * @param point the point to be used
     * @param includeAlt if altitude should be included in the human readable string
     * @return the humand readable string for the point
     */
    public String formatPoint(GeoPoint point, boolean includeAlt) {
        return formatPoint(new GeoPointMetaData(point), includeAlt);
    }

    /**
     * Produce a human-readable altitude string
     * @param pointMD Altitude point
     * @return Altitude string
     */
    public String formatAltitude(GeoPointMetaData pointMD) {
        Span altUnits = getAltitudeUnits();

        String ret = _context.getString(R.string.ft_msl2);
        if (pointMD == null || !pointMD.get().isValid())
            return ret;

        GeoPoint point = pointMD.get();
        if (get("alt_display_agl", false)) {
            GeoPointMetaData groundElev = ElevationManager
                    .getElevationMetadata(point);
            if (groundElev.get().isValid()) {
                double altM = EGM96.getAGL(point,
                        groundElev.get().getAltitude());
                ret = EGM96.formatAGL(altM, altUnits);
            } else {
                //unable to display AGL w/out a ground reference point for this location
                ret = "-- ft AGL";
            }
        } else {
            //just use fixed MSL or HAE based on prefs
            String altRef = getAltitudeReference();
            if (altRef.equals("MSL"))
                ret = EGM96.formatMSL(point, altUnits);
            else
                ret = EGM96.formatHAE(point, altUnits);
        }

        // Altitude source
        String altSrc = pointMD.getAltitudeSource();
        if (!FileSystemUtils.isEmpty(altSrc)
                && !altSrc.equals(GeoPointMetaData.UNKNOWN))
            ret += " " + altSrc;

        return ret;
    }

    /**
     * Produce a human-readable altitude string
     * @param point Altitude point
     * @return Altitude string
     */
    public String formatAltitude(GeoPoint point) {
        return formatAltitude(new GeoPointMetaData(point));
    }
}
