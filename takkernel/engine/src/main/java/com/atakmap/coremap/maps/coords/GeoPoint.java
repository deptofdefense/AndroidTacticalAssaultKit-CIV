
package com.atakmap.coremap.maps.coords;

import android.os.Parcel;
import android.os.Parcelable;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Description of a geospatial point latitude, longitude and altitude with the associated error elipses.
 */
public final class GeoPoint implements Parcelable {

    public final static String TAG = "GeoPoint";

    private static final Pattern comma = Pattern.compile(","); // thread safe

    public enum DistanceCalculation {
        SLANT,
        SURFACE
    }

    public enum Access {
        READ_ONLY,
        READ_WRITE
    }

    public final static double UNKNOWN = Double.NaN;

    public final static GeoPoint ZERO_POINT = new GeoPoint(0d, 0d, UNKNOWN,
            AltitudeReference.HAE, UNKNOWN, UNKNOWN, Access.READ_ONLY);
    public final static GeoPoint UNKNOWN_POINT = new GeoPoint(UNKNOWN, UNKNOWN,
            UNKNOWN, AltitudeReference.HAE, UNKNOWN, UNKNOWN, Access.READ_ONLY);

    public static final double MIN_ACCEPTABLE_ALTITUDE = -3600;
    private static final double MAX_ACCEPTABLE_ALTITUDE = 76000;

    public enum AltitudeReference {
        HAE,
        AGL;

        public static AltitudeReference get(String name) {
            for (AltitudeReference ref : AltitudeReference.values()) {
                if (ref.name().equals(name))
                    return ref;
            }
            return HAE;
        }
    }

    private double latitude;
    private double longitude;
    private double altitude;
    private AltitudeReference altitudeReference;
    private double ce90;
    private double le90;
    private Access access;

    /**
     * Full constructor for a GeoPoint.
     *
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     * @param altitude the altitude provided in meters [-3600, 78000]   
     * @param altitudeReference the altitude refernece in either AltitudeReference.HAE or AltitudeReference.AGL
     * @param ce90 circular error of 90 percent for the horizontal. A CE90 value is the minimum
     *            diameter of the horizontal circle that can be centered on all photo-identifiable
     *            Ground Control Points (GCPs) and also contain 90% of their respective twin
     *            counterparts acquired in an independent geodetic survey.
     * @param le90 linear error of 90 percent for the vertical. A LE90 value represents the linear
     *            vertical distance that 90% of control points and their respective twin matching
     *            counterparts acquired in an independent geodetic survey should be found from each
     *            other.
     * @param access the access for this geopoint as either Access.READ_ONLY or Access.READ_WRITE
     */
    public GeoPoint(final double latitude,
            final double longitude,
            final double altitude,
            final AltitudeReference altitudeReference,
            final double ce90,
            final double le90,
            final Access access) {
        this.latitude = latitude;
        this.longitude = longitude;
        if (altitude < MIN_ACCEPTABLE_ALTITUDE
                || altitude > MAX_ACCEPTABLE_ALTITUDE) {
            this.altitude = UNKNOWN;
        } else {
            this.altitude = altitude;
        }

        this.altitudeReference = altitudeReference;
        this.ce90 = ce90;
        this.le90 = le90;
        this.access = access;
    }

    /**
     * Constructs a read only geopoint with all of the parameter.
     *
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     * @param altitude the altitude provided in meters
     * @param altitudeReference the altitude refernece in either AltitudeReference.HAE or AltitudeReference.AGL
     * @param ce90 circular error of 90 percent for the horizontal. A CE90 value is the minimum
     *            diameter of the horizontal circle that can be centered on all photo-identifiable
     *            Ground Control Points (GCPs) and also contain 90% of their respective twin
     *            counterparts acquired in an independent geodetic survey.
     * @param le90 linear error of 90 percent for the vertical. A LE90 value represents the linear
     *            vertical distance that 90% of control points and their respective twin matching
     *            counterparts acquired in an independent geodetic survey should be found from each
     *            other.
     */
    public GeoPoint(final double latitude,
            final double longitude,
            final double altitude,
            final AltitudeReference altitudeReference,
            final double ce90,
            final double le90) {
        this(latitude, longitude, altitude, altitudeReference, ce90, le90,
                Access.READ_ONLY);
    }

    /**
     *  Constructs a read only geopoint with the altitude specified a @see AltitudeReference.HAE.
     *
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     * @param altitude the altitude in HAE METERS
     */
    public GeoPoint(final double latitude,
            final double longitude,
            final double altitude,
            final double ce90,
            final double le90) {

        this(latitude, longitude, altitude, AltitudeReference.HAE,
                ce90, le90, Access.READ_ONLY);
    }

    /**
     * Constructs a GeoPoint with an unknown GeoPoint.ALTITUDE_UNKNOWN and CE90 and LE90 set to
     *             UNKNOWN.
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     */
    public GeoPoint(final double latitude, final double longitude) {
        this(latitude, longitude, UNKNOWN, AltitudeReference.HAE,
                UNKNOWN, UNKNOWN, Access.READ_ONLY);
    }

    /**
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     * @param altitude the altitude in HAE METERS
     */
    public GeoPoint(final double latitude,
            final double longitude,
            final double altitude) {

        this(latitude, longitude, altitude, AltitudeReference.HAE,
                UNKNOWN, UNKNOWN, Access.READ_ONLY);
    }

    /**
     * @param latitude the latitude position in decimal degrees [-90, 90]
     * @param longitude the longitude position in decimal degrees [-180, 180]
     * @param altitude the altitude in METERS specified by the altitudeReference
     * @param altitudeReference the altitude reference 
     */
    public GeoPoint(final double latitude,
            final double longitude,
            final double altitude,
            final AltitudeReference altitudeReference) {

        this(latitude, longitude, altitude, altitudeReference,
                UNKNOWN, UNKNOWN, Access.READ_ONLY);
    }

    /**
     * Do not allow for empty construction of a GeoPoint. Mark as private.
     */
    private GeoPoint() {
        longitude = Double.NaN;
        latitude = Double.NaN;
        altitude = UNKNOWN;
        altitudeReference = AltitudeReference.HAE;
        ce90 = UNKNOWN;
        le90 = UNKNOWN;
        access = Access.READ_ONLY;
    }

    /**
     * Copy constructor for a GeoPoint which allows for modifying the access of the copy.
     * @param other the geopoint to copy.
     * @param access the access for the GeoPoint of either Access.READ_ONLY or Access.READ_WRITE
     */
    public GeoPoint(GeoPoint other, Access access) {
        this(other.latitude, other.longitude, other.altitude,
                other.altitudeReference, other.ce90, other.le90, access);
    }

    /**
     * Copy constructor for a GeoPoint which perserves the access permissions of the original geopoint.
     * @param other the geopoint to copy.
     */
    public GeoPoint(GeoPoint other) {
        this(other.latitude, other.longitude, other.altitude,
                other.altitudeReference, other.ce90, other.le90, other.access);
    }

    /**
     * Implementation of the Parcel constructor.
     * @param p the parcel to read from.
     */
    public GeoPoint(Parcel p) {
        this(p.readDouble(),
                p.readDouble(),
                p.readDouble(),
                AltitudeReference.values()[p.readInt()],
                p.readDouble(),
                p.readDouble(),
                Access.values()[p.readInt()]);

    }

    /**
     * Creates a mutable Geopoint which is initially invalid.
     * @return the mutable geopoint.
     */
    public static GeoPoint createMutable() {
        GeoPoint ret = new GeoPoint();
        ret.access = Access.READ_WRITE;
        return ret;
    }

    /**
     * Checks the validity of the GeoPoint
     * @return returns true if the GeoPoint is valid.
     */
    public boolean isValid() {
        return isValid(latitude, longitude);
    }

    /**
     * Checks the validity of the GeoPoints altitude.   The altitude of a TAK GeoPoint is 
     * bounded to be [-3600, 76000]
     * @return returns true if the Altitude is valid.
     */
    public boolean isAltitudeValid() {
        return !Double.isNaN(altitude) &&
                altitude <= MAX_ACCEPTABLE_ALTITUDE &&
                altitude >= MIN_ACCEPTABLE_ALTITUDE;
    }

    /**
     * Checks the validity of the provided latitude and longitude.
     * @param latitude the latitude as specified in degrees.
     * @param longitude the longitude as specified in degrees.
     * @return returns true if the latitude and longitude are valid.
     */
    public static boolean isValid(double latitude, double longitude) {
        return latitude >= -90d && longitude >= -180d &&
                latitude <= 90d && longitude <= 180d &&
                !Double.isNaN(latitude) &&
                !Double.isNaN(longitude);
    }

    /**
     * Checks the validity of a raw altitude.   The altitude of a TAK GeoPoint is 
     * bounded to be [-3600, 76000]
     * @return returns true if the Altitude is valid.
     */
    public static boolean isAltitudeValid(final double altitude) {
        return !Double.isNaN(altitude) &&
                altitude <= MAX_ACCEPTABLE_ALTITUDE &&
                altitude >= MIN_ACCEPTABLE_ALTITUDE;
    }

    /**
     * Is the geopoint capable of of being changed with the appropriate set methods.
     * @return true if it is mutable.
     */
    public boolean isMutable() {
        return access == Access.READ_WRITE;
    }

    /**
     * Implementation of a convienence method for calculating a distance from one geopoint to
     * another geopoint
     * @param p the point to measure to.
     * @return the distance in meters.
     */
    public double distanceTo(final GeoPoint p) {
        return GeoCalculations.distanceTo(this, p);
    }

    /**
     * Implementation of a convienence method for calculating a bearing from one geopoint to
     * another geopoint
     * @param p the point to measure to.
     * @return the distance in meters.
     */
    public double bearingTo(final GeoPoint p) {
        return GeoCalculations.bearingTo(this, p);
    }

    /**
     * Get the latitude position
     *
     * @return latitude in decimal degrees
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Get the longitude position
     *
     * @return decimal degrees
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * Returns the altitude for the GeoPoint.
     */
    public double getAltitude() {
        return altitude;
    }

    /**
     * Returns the altitude reference for the GeoPoint.
     */
    public AltitudeReference getAltitudeReference() {
        return altitudeReference;
    }

    /**
     * Returns the circular error of 90 percent for the horizontal.
     * @return the circular error, Double.NaN if invalid
     */
    public double getCE() {
        return ce90;
    }

    /**
     * Returns the linear error of 90 percent for the vertical.
     * @return the linear error, Double.NaN if invalid
     */
    public double getLE() {
        return le90;
    }

    /**
     * Get a human readable representation of the GeoPoint
     *
     * Valid formats include:
     * lat,lng
     * lat,lng,alt(HAE)
     * lat,lng,alt,altReference
     * lat,lng,alt,altReference,CE,LE
     *
     * There are tools that currently make use of this format for machine
     * to machine communications
     *
     * @param numArgs Number of arguments (2, 3, 4, or 6)
     * @return Geo point string
     */
    public String toString(int numArgs) {
        if (numArgs < 2 || numArgs > 6)
            return "";
        StringBuilder sb = new StringBuilder();
        sb.append(latitude).append(",");
        sb.append(longitude);
        if (numArgs >= 3) {
            if (numArgs == 3 && altitudeReference != AltitudeReference.HAE)
                sb.append(",").append(EGM96.getHAE(this));
            else
                sb.append(",").append(altitude);
        }
        if (numArgs >= 4)
            sb.append(",").append(altitudeReference);
        if (numArgs >= 5)
            sb.append(",").append(ce90);
        if (numArgs >= 6)
            sb.append(",").append(le90);
        return sb.toString();
    }

    /**
     * Get a human readable representation of the GeoPoint in the format:
     * latitude, longitude, altitude (HAE)
     */
    @Override
    public String toString() {
        return toString(isAltitudeValid() ? 3 : 2);
    }

    /**
     * Get a human readable representation of the GeoPoint in the format:
     *      latitude,longitude,altitude,altitudeReference,ce90,le90
     */
    public String toStringRepresentation() {
        return toString(6);
    }

    /**
     * Given a String of format lat, lon or lat, lon, alt where all values are stored in the double
     * type, this method returns a GeoPoint instance.
     *
     * @param str - A String containing a coordinate pair or a coordinate pair plus altitude. ALl
     *            values are to be delimited by a comma. Example: "34.831, -112.243" or
     *            "34.831, -112.243, 543.2" are valid values. The ALTITUDE, if present is in HAE /
     *            METERS.   There is also a 6 parameter serialization produced by the toString
     *            functionality in this class.
     *
     * @return A GeoPoint instance or null if the String cannot be parsed.
     */
    public static GeoPoint parseGeoPoint(String str) {
        if (str == null) {
            return null;
        }
        String[] parts = comma.split(str, 0);

        // for some reason the point has an invalid // format
        if (parts.length < 2) {
            Log.e(TAG, "unable to parse: " + str);
            return null;
        }

        if ("".equals(parts[0]) || "".equals(parts[1])) {
            Log.e(TAG, "unable to parse: " + str);
            return null;
        }

        double lat;
        double lon;

        try {
            lat = Double.parseDouble(parts[0].trim());
            lon = Double.parseDouble(parts[1].trim());
        } catch (Exception e) {
            Log.d(TAG, "error occurred parsing the lat/lon", e);
            return null;
        }
        double altitude = Double.NaN;
        AltitudeReference altitudeReference = AltitudeReference.HAE;
        double ce = UNKNOWN;
        double le = UNKNOWN;
        try {
            if (parts.length > 2)
                altitude = Double.parseDouble(parts[2].trim());
            if (parts.length > 3)
                altitudeReference = AltitudeReference.get(parts[3].trim());
            if (parts.length > 4)
                ce = Double.parseDouble(parts[4].trim());
            if (parts.length > 5)
                le = Double.parseDouble(parts[5].trim());
        } catch (Exception e) {
            Log.d(TAG, "error occurred parsing the geopoint: " + str, e);
            return null;
        }
        return new GeoPoint(lat, lon, altitude, altitudeReference, ce, le);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel p, final int flags) {
        p.writeDouble(latitude);
        p.writeDouble(longitude);
        p.writeDouble(altitude);
        p.writeInt(altitudeReference.ordinal());
        p.writeDouble(ce90);
        p.writeDouble(le90);
        p.writeInt(access.ordinal());
    }

    /**
     * Create a GeoPoint from a parcel.
     */
    public GeoPoint readFromParcel(final Parcel p) {
        return new GeoPoint(p);
    }

    public static final Parcelable.Creator<GeoPoint> CREATOR = new Parcelable.Creator<GeoPoint>() {
        @Override
        public GeoPoint createFromParcel(Parcel p) {
            return new GeoPoint(p);
        }

        @Override
        public GeoPoint[] newArray(int count) {
            return new GeoPoint[count];
        }
    };

    /**
     * Sets the parameters of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param latitude the latitude of the geopoint.
     * @param longitude the longitude of the geopoint.
     * @param altitude the altitude in as specified by the altitude reference
     * @param altitudeReference the altitude reference for the provided altitude.
     * @param ce90 the circular error
     * @param le90 the linear error.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public boolean set(final double latitude, final double longitude,
            final double altitude,
            final AltitudeReference altitudeReference, final double ce90,
            final double le90) {
        if (access == Access.READ_ONLY)
            return false;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.altitudeReference = altitudeReference;
        this.ce90 = ce90;
        this.le90 = le90;
        return true;
    }

    /**
     * Sets the parameters of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param gp the geopoint to use as the source to copy from.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public GeoPoint set(GeoPoint gp) {
        if (access == Access.READ_ONLY)
            return null;
        this.latitude = gp.getLatitude();
        this.longitude = gp.getLongitude();
        this.altitude = gp.getAltitude();
        this.altitudeReference = gp.getAltitudeReference();
        this.ce90 = gp.getCE();
        this.le90 = gp.getLE();
        return this;
    }

    /**
     * Sets the parameters of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param latitude the latitude of the geopoint.
     * @param longitude the longitude of the geopoint.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public GeoPoint set(final double latitude, final double longitude) {
        if (access == Access.READ_ONLY)
            return null;
        this.latitude = latitude;
        this.longitude = longitude;
        this.ce90 = UNKNOWN;
        this.le90 = UNKNOWN;
        return this;
    }

    /**
     * Sets the parameters of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param latitude the latitude of the geopoint.
     * @param longitude the longitude of the geopoint.
     * @param altitude the altitude in HAE.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public GeoPoint set(final double latitude, final double longitude,
            final double altitude) {
        if (access == Access.READ_ONLY)
            return null;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.altitudeReference = AltitudeReference.HAE;
        this.ce90 = UNKNOWN;
        this.le90 = UNKNOWN;
        return this;
    }

    /**
     * Sets the parameters of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param latitude the latitude of the geopoint.
     * @param longitude the longitude of the geopoint.
     * @param altitude the altitude in HAE.
     * @param ce90 the circular error
     * @param le90 the linear error.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public GeoPoint set(final double latitude, final double longitude,
            final double altitude,
            final double ce90, final double le90) {
        if (access == Access.READ_ONLY)
            return null;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.altitudeReference = AltitudeReference.HAE;
        this.ce90 = ce90;
        this.le90 = le90;
        return this;
    }

    /**
     * Sets the altitude of the GeoPoint provided the GeoPoint access is READ_WRITE.
     * @param altitude the altitude in HAE.
     * @return a reference to the same GeoPoint, null if the GeoPoint was not READ_WRITE
     */
    public GeoPoint set(final double altitude) {
        if (access == Access.READ_ONLY)
            return null;

        this.altitude = altitude;
        this.altitudeReference = AltitudeReference.HAE;
        this.ce90 = UNKNOWN;
        this.le90 = UNKNOWN;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GeoPoint geoPoint = (GeoPoint) o;
        return Double.compare(geoPoint.latitude, latitude) == 0 &&
                Double.compare(geoPoint.longitude, longitude) == 0 &&
                Double.compare(geoPoint.altitude, altitude) == 0 &&
                Double.compare(geoPoint.ce90, ce90) == 0 &&
                Double.compare(geoPoint.le90, le90) == 0 &&
                altitudeReference == geoPoint.altitudeReference;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, altitude, altitudeReference,
                ce90, le90);
    }
}
