
package com.atakmap.coremap.cot.event;

import java.io.IOException;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.coremap.maps.conversion.EGM96;

/**
 * A Cursor on Target point.
 * 
 * 
 */
public class CotPoint implements Parcelable {

    public static final String TAG = "CotPoint";

    public static final double UNKNOWN = 9999999;

    /**
     * Constant for decimating the latitude and longitude for 7 digits of precision (nanometer)
     */
    private static final double PRECISION_7 = 10000000;

    /**
     * Constant for decimating the altitude for 4 digits of precision (nanometer)
     */
    private static final double PRECISION_4 = 1000;

    /**
     * Constant for decimating the le/ce for 1 digits of precision (centimeter)
     */
    private static final double PRECISION_1 = 10;

    /**
     * Point where latitude, longitude, and height above ellipsoid equal UNKNOWN_ALTITUDE. Linear
     * error and circular error are UNKNOWN_CE90, UNKNOWN_LE90.
     */
    public static final CotPoint ZERO = new CotPoint(0d, 0d,
            UNKNOWN,
            UNKNOWN,
            UNKNOWN);

    private final double _lat;
    private final double _lon;
    private double _hae;
    private double _ce;
    private double _le;

    /**
     * Create a CoT point given all point attributes
     * 
     * @param lat latitude
     * @param lon longitude
     * @param hae height above ellipsoid in meters
     * @param ce circular radius error in meters (set to CotPoint.COT_CE90_UNKNOWN if unknown)
     * @param le linear error in meters (set to CotPoint.COT_LE90_UNKNOWN if unknown)
     */
    public CotPoint(final double lat,
            final double lon,
            final double hae,
            final double ce,
            final double le) {
        _lat = lat;
        _lon = lon;
        _hae = isInvalid(hae) ? UNKNOWN : hae;
        _ce = isInvalid(ce) ? UNKNOWN : ce;
        _le = isInvalid(le) ? UNKNOWN : le;
    }

    private static boolean isInvalid(double d) {
        return (Double.compare(d, UNKNOWN) == 0 || Double.isNaN(d));
    }

    /**
     * Create a CoT point given a GeoPoint.
     */
    public CotPoint(GeoPoint gp) {
        this(gp.getLatitude(),
                gp.getLongitude(),
                (!gp.isAltitudeValid()) ? UNKNOWN
                        : EGM96.getHAE(gp),
                (isInvalid(gp.getCE())) ? UNKNOWN
                        : gp.getCE(),
                (isInvalid(gp.getLE())) ? UNKNOWN
                        : gp.getLE());

    }

    /**
     * Construct a GeoPoint from a CotPoint.
     */
    public GeoPoint toGeoPoint() {
        final double ce = getCe();
        final double le = getLe();

        return new GeoPoint(getLat(), getLon(), getHae(), AltitudeReference.HAE,
                isInvalid(ce) ? GeoPoint.UNKNOWN : ce,
                isInvalid(le) ? GeoPoint.UNKNOWN : le);
    }

    /**
     * Create a CoT point from its Parcel representation
     * 
     * @param source the parcel representation of the CoT point.
     */
    public CotPoint(Parcel source) {
        _lat = source.readDouble();
        _lon = source.readDouble();
        _hae = source.readDouble();
        _ce = source.readDouble();
        _le = source.readDouble();
    }

    /**
     * Copy Constructor
     * 
     * @param point the point to copy
     */
    public CotPoint(CotPoint point) {
        _lat = point._lat;
        _lon = point._lon;
        _hae = point._hae;
        _ce = point._ce;
        _le = point._le;
    }

    /**
     * Build upon a StringBuilder for the XML representation of this point.
     *
     */
    public void buildXml(Appendable b) throws IOException {
        b.append("<point lat='");
        b.append(String.valueOf(decimate(_lat, PRECISION_7)));
        b.append("' lon='");
        b.append(String.valueOf(decimate(_lon, PRECISION_7)));
        b.append("' hae='");
        b.append(String.valueOf(decimate(_hae, PRECISION_4)));
        b.append("' ce='");
        b.append(String.valueOf(decimate(_ce, PRECISION_1)));
        b.append("' le='");
        b.append(String.valueOf(decimate(_le, PRECISION_1)));
        b.append("' />");
    }

    /**
     * Get the latitude
     * 
     * @return the latitude
     */
    public double getLat() {
        return _lat;
    }

    /**
     * Get the longitude
     * 
     * @return the longitude
     */
    public double getLon() {
        return _lon;
    }

    /**
     * Get the circular radius error in meters
     * 
     * @return UNKNOWN if the error is unknown
     */
    public double getCe() {
        if (Double.isNaN(_ce))
            return UNKNOWN;
        return _ce;
    }

    /**
     * Get the linear error in meters
     * 
     * @return UNKNOWN if the error is unknown
     */
    public double getLe() {
        if (Double.isNaN(_le))
            return UNKNOWN;
        return _le;
    }

    /**
     * Get the height above ellipsoid in meters
     * @return UNKNOWN if the altitude is unknown
     * 
     * @return the HAE of the altitude in meters
     */
    public double getHae() {
        if (Double.isNaN(_hae))
            return UNKNOWN;
        return _hae;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String toString() {
        return "" + _lat + ", " + _lon + ", " + _hae + ", " + _ce + ", " + _le;
    }

    /**
     * Returns a decimated string representation of a GeoPoint that mimics the precision defined in
     * LocationMapComponent.
     * @param pt the geopoint to decimate so that it is 7 digits of precision for the lat and lon and 4
     * digits of precision for altitude.
     * @return string representation of a geopoint in lat,lon,altHae
     */
    public static String decimate(final GeoPoint pt) {
        double lat = decimate(pt.getLatitude(), PRECISION_7);
        double lon = decimate(pt.getLongitude(), PRECISION_7);

        if (!pt.isAltitudeValid())
            return lat + "," + lon;

        double alt = decimate(EGM96.getHAE(pt), PRECISION_4);
        return lat + "," + lon + "," + alt;
    }

    /**
     * Decimate a double based on the provided precision.
     * @param val the value to decimate
     * @param precision the precision to use.
     */
    private static double decimate(final double val, final double precision) {
        return Math.round(val * precision) / precision;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(_lat);
        dest.writeDouble(_lon);
        dest.writeDouble(_hae);
        dest.writeDouble(_ce);
        dest.writeDouble(_le);
    }

    public static final Parcelable.Creator<CotPoint> CREATOR = new Parcelable.Creator<CotPoint>() {
        @Override
        public CotPoint createFromParcel(Parcel source) {
            return new CotPoint(source);
        }

        @Override
        public CotPoint[] newArray(int size) {
            return new CotPoint[size];
        }
    };

}
