// Created by plusminus on 21:28:12 - 25.09.2008
package transapps.geom;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

/**
 *
 * Implementation of the {@link Coordinate} interface that uses latitude and longitude in E6 format for its
 * X and Y points and calculating the bearing and distances.
 *
 * @author Nicolas Gramlich
 * @author Theodore Hong
 *
 */
public class GeoPoint extends Coord implements Parcelable, Cloneable {


    // ===========================================================
    // Constructors
    // ===========================================================
    
    public GeoPoint() {
    }

    public GeoPoint(final int aLatitudeE6, final int aLongitudeE6) {
        super(aLongitudeE6, aLatitudeE6);
    }

    public GeoPoint(final int aLatitudeE6, final int aLongitudeE6, final int aAltitude) {
        this(aLatitudeE6, aLongitudeE6);
        setZ(aAltitude);
    }

    public GeoPoint(final double aLatitude, final double aLongitude) {
        this((int) Math.round(aLatitude * 1E6), (int) Math.round(aLongitude * 1E6));
    }

    public GeoPoint(final double aLatitude, final double aLongitude, final double aAltitude) {
        this(aLatitude, aLongitude);
        setZ((int) aAltitude);
    }

    public GeoPoint(final Location aLocation) {
        this(aLocation.getLatitude(), aLocation.getLongitude(), aLocation.getAltitude());
    }

    public GeoPoint(final GeoPoint aGeopoint) {
        this(aGeopoint.y, aGeopoint.x, aGeopoint.z);
    }

    /**
     *
     * Static method to convert from a decimal string to a GeoPoint using the provided delimiter between the
     * latitude and longitude value.
     *
     * @param s  string with the latitude and longitude values
     * @param spacer  delimiter character between the latitude and longitude portion of the string
     * @return  Geopoint representation of the string passed in
     */
    public static GeoPoint fromDoubleString(final String s, final char spacer) {
        final int spacerPos1 = s.indexOf(spacer);
        final int spacerPos2 = s.indexOf(spacer, spacerPos1 + 1);

        if (spacerPos2 == -1) {
            return new GeoPoint(
                    (int) (Double.parseDouble(s.substring(0, spacerPos1)) * 1E6),
                    (int) (Double.parseDouble(s.substring(spacerPos1 + 1, s.length())) * 1E6));
        } else {
            return new GeoPoint(
                    (int) (Double.parseDouble(s.substring(0, spacerPos1)) * 1E6),
                    (int) (Double.parseDouble(s.substring(spacerPos1 + 1, spacerPos2)) * 1E6),
                    (int) Double.parseDouble(s.substring(spacerPos2 + 1, s.length())));
        }
    }

    /**
     *
     * Static method to convert from a decimal string to a GeoPoint using the provided delimiter between the
     * inverted order of longitude then latitude value.
     *
     * @param s  string with the longitude and latitude values
     * @param spacer  delimiter character between the latitude and longitude portion of the string
     * @return  Geopoint representation of the string passed in
     */
    public static GeoPoint fromInvertedDoubleString(final String s, final char spacer) {
        final int spacerPos1 = s.indexOf(spacer);
        final int spacerPos2 = s.indexOf(spacer, spacerPos1 + 1);

        if (spacerPos2 == -1) {
            return new GeoPoint(
                    (int) (Double.parseDouble(s.substring(spacerPos1 + 1, s.length())) * 1E6),
                    (int) (Double.parseDouble(s.substring(0, spacerPos1)) * 1E6));
        } else {
            return new GeoPoint(
                    (int) (Double.parseDouble(s.substring(spacerPos1 + 1, spacerPos2)) * 1E6),
                    (int) (Double.parseDouble(s.substring(0, spacerPos1)) * 1E6),
                    (int) Double.parseDouble(s.substring(spacerPos2 + 1, s.length())));

        }
    }

    /**
     * Static method to convert from a string with E6 integer latitude and longitude values, delimited by a comma.
     *
     * @param s    string containing the E6 integer latitude and longitude values
     * @return     the GeoPoint created from the passed in string.
     */
    public static GeoPoint fromIntString(final String s) {
        final int commaPos1 = s.indexOf(',');
        final int commaPos2 = s.indexOf(',', commaPos1 + 1);

        if (commaPos2 == -1) {
            return new GeoPoint(
                    Integer.parseInt(s.substring(0, commaPos1)),
                    Integer.parseInt(s.substring(commaPos1 + 1, s.length())));
        } else {
            return new GeoPoint(
                    Integer.parseInt(s.substring(0, commaPos1)),
                    Integer.parseInt(s.substring(commaPos1 + 1, commaPos2)),
                    Integer.parseInt(s.substring(commaPos2 + 1, s.length()))
            );
        }
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    public int getLongitudeE6() {
        return this.x;
    }

    public int getLatitudeE6() {
        return this.y;
    }

    public int getAltitude() {
        return this.z;
    }

    public void setLongitudeE6(final int aLongitudeE6) {
        this.x = aLongitudeE6;
    }

    public void setLatitudeE6(final int aLatitudeE6) {
        this.y = aLatitudeE6;
    }

    public void setAltitude(final int aAltitude) {
        this.z = aAltitude;
    }

    public void setCoordsE6(final int aLatitudeE6, final int aLongitudeE6) {
        this.y = aLatitudeE6;
        this.x = aLongitudeE6;
    }

    // ===========================================================
    // Methods from SuperClass/Interfaces
    // ===========================================================

    @Override
    public Object clone() {
        return new GeoPoint(this.y, this.x);
    }

    @Override
    public String toString() {
        return new StringBuilder().append(this.y).append(",").append(this.x).append(",").append(this.z)
        .toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        final GeoPoint rhs = (GeoPoint) obj;
        return rhs.y == this.y && rhs.x == this.x && rhs.z == this.z;
    }

    @Override
    public int hashCode() {
        return 37 * (17 * x + y) + z;
    }

    // ===========================================================
    // Parcelable
    // ===========================================================
    
    public static final Parcelable.Creator<GeoPoint> CREATOR = new Parcelable.Creator<GeoPoint>() {
        @Override
        public GeoPoint createFromParcel(final Parcel in) {
            return new GeoPoint(in);
        }

        @Override
        public GeoPoint[] newArray(final int size) {
            return new GeoPoint[size];
        }
    };
    
    private GeoPoint(final Parcel in) {
        super(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // ===========================================================
    // Methods
    // ===========================================================

    /**
     * @see Source@ http://www.geocities.com/DrChengalva/GPSDistance.html
     * @return distance in meters
     */
    @Override
    public int distanceTo(final Coordinate other) {        
        final double a1 = GeoConstants.DEG2RAD * this.y / 1E6;
        final double a2 = GeoConstants.DEG2RAD * this.x / 1E6;
        final double b1 = GeoConstants.DEG2RAD * other.getY() / 1E6;
        final double b2 = GeoConstants.DEG2RAD * other.getX() / 1E6;

        final double cosa1 = Math.cos(a1);
        final double cosb1 = Math.cos(b1);

        final double t1 = cosa1 * Math.cos(a2) * cosb1 * Math.cos(b2);

        final double t2 = cosa1 * Math.sin(a2) * cosb1 * Math.sin(b2);

        final double t3 = Math.sin(a1) * Math.sin(b1);

        final double tt = Math.acos(t1 + t2 + t3);

        return (int) (GeoConstants.RADIUS_EARTH_METERS * tt);
    }

    /**
     * @see Source@ http://groups.google.com/group/osmdroid/browse_thread/thread/d22c4efeb9188fe9/
     *      bc7f9b3111158dd
     * @return bearing in degrees
     */
    @Override
    public float bearingTo(final Coordinate other) {
        final double lat1 = Math.toRadians(this.y / 1E6);
        final double long1 = Math.toRadians(this.x / 1E6);
        final double lat2 = Math.toRadians(other.getY() / 1E6);
        final double long2 = Math.toRadians(other.getX() / 1E6);
        final double delta_long = long2 - long1;
        final double a = Math.sin(delta_long) * Math.cos(lat2);
        final double b = Math.cos(lat1) * Math.sin(lat2) -
                         Math.sin(lat1) * Math.cos(lat2) * Math.cos(delta_long);
        final double bearing = Math.toDegrees(Math.atan2(a, b));
        final double bearing_normalized = (bearing + 360) % 360;
        return (float) bearing_normalized;
    }

    /**
     * Calculate a point that is the specified distance and bearing away from this point.
     *
     * @see Source@ http://www.movable-type.co.uk/scripts/latlong.html
     * @see Source@ http://www.movable-type.co.uk/scripts/latlon.js
     */
    public GeoPoint destinationPoint(final double aDistanceInMeters, final float aBearingInDegrees) {

        // convert distance to angular distance
        final double dist = aDistanceInMeters / GeoConstants.RADIUS_EARTH_METERS;

        // convert bearing to radians
        final float brng = GeoConstants.DEG2RAD * aBearingInDegrees;

        // get current location in radians
        final double lat1 = GeoConstants.DEG2RAD * getLatitudeE6() / 1E6;
        final double lon1 = GeoConstants.DEG2RAD * getLongitudeE6() / 1E6;

        final double lat2 = Math.asin(Math.sin(lat1) * Math.cos(dist) + Math.cos(lat1)
                * Math.sin(dist) * Math.cos(brng));
        final double lon2 = lon1
        + Math.atan2(Math.sin(brng) * Math.sin(dist) * Math.cos(lat1), Math.cos(dist)
                - Math.sin(lat1) * Math.sin(lat2));

        final double lat2deg = lat2 / GeoConstants.DEG2RAD;
        final double lon2deg = lon2 / GeoConstants.DEG2RAD;

        return new GeoPoint(lat2deg, lon2deg);
    }

    @Override
    public String toDoubleString() {
        return new StringBuilder().append(this.y / 1E6).append(",")
        .append(this.x / 1E6).append(",").append(this.z).toString();
    }

    public String toInvertedDoubleString() {
        return new StringBuilder().append(this.x / 1E6).append(",")
        .append(this.y / 1E6).append(",").append(this.z).toString();
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
}
