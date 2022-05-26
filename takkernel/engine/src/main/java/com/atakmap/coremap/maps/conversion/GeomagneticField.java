
package com.atakmap.coremap.maps.conversion;

import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.Calendar;

public final class GeomagneticField {
    private final float latitude;
    private final float longitude;
    private final float hae;

    private final int date;
    private final int month;
    private final int year;

    /**
     * Estimate the magnetic field at a given point and time.
     *
     * @param latitude
     *            Latitude in WGS84 geodetic coordinates -- positive is east.
     * @param longitude
     *            Longitude in WGS84 geodetic coordinates -- positive is north.
     * @param hae
     *            Altitude in WGS84 geodetic coordinates, in meters (HAE).
     * @param millis
     *            Time at which to evaluate the declination, in milliseconds
     *            since January 1, 1970. (approximate is fine -- the declination
     *            changes very slowly).
     */
    public GeomagneticField(float latitude, float longitude, float hae,
            long millis) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.hae = hae;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);

        this.date = cal.get(Calendar.DATE);
        this.month = cal.get(Calendar.MONTH);
        this.year = cal.get(Calendar.YEAR);
    }

    /**
     * Obtain the declination
     * @return the magnetic declination for the location at the provided time.
     */
    public float getDeclination() {
        return getDeclination(latitude, longitude, hae, year, month, date);
    }

    /**
     * @return  Total field strength in nanoteslas.
     */
    public double getFieldStrength() {
        return getFieldStrength(latitude, longitude, hae, year, month, date);
    }

    /**
     * @return  Horizontal component of the field strength in nanoteslas.
     */
    public double getHorizontalStrength() {
        return getHorizontalStrength(latitude, longitude, hae, year, month, date);
    }

    /**
     * @return The inclination of the magnetic field in degrees -- positive
     *         means the magnetic field is rotated downwards.
     */
    public double getInclination() {
        return getInclination(latitude, longitude, hae, year, month, date);
    }

    /**
     * @return The X (northward) component of the magnetic field in nanoteslas.
     */
    public double getX() {
        return getX(latitude, longitude, hae, year, month, date);
    }

    /**
     * @return The Y (eastward) component of the magnetic field in nanoteslas.
     */
    public double getY() {
        return getY(latitude, longitude, hae, year, month, date);
    }

    /**
     * @return The Z (downward) component of the magnetic field in nanoteslas.
     */
    public float getZ() {
        return getZ(latitude, longitude, hae, year, month, date);
    }

    /**
     * @return The declination of the horizontal component of the magnetic
     *         field from true north, in degrees (i.e. positive means the
     *         magnetic field is rotated east that much from true north).
     */
    public static double getDeclination(double latitude, double longitude,
            double hae) {
        final Calendar time = Calendar.getInstance();
        time.setTimeInMillis(CoordinatedTime.currentTimeMillis());

        return getDeclination(latitude, longitude, hae,
                time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DATE));

    }

    /**
     * Estimate the Total field strength in nanoteslas at the current time
     *
     * @param latitude
     *            Latitude in WGS84 geodetic coordinates -- positive is east.
     * @param longitude
     *            Longitude in WGS84 geodetic coordinates -- positive is north.
     * @param hae
     *            Altitude in WGS84 geodetic coordinates, in meters (HAE).
     * @return  Total field strength in nanoteslas.
     */
    public static double getFieldStrength(double latitude, double longitude,
            double hae) {
        final Calendar time = Calendar.getInstance();
        time.setTimeInMillis(CoordinatedTime.currentTimeMillis());

        return getFieldStrength(latitude, longitude, hae,
                time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DATE));
    }

    /**
     * Estimate the Horizontal component of the field strength in nanoteslas.
     *
     * @param latitude
     *            Latitude in WGS84 geodetic coordinates -- positive is east.
     * @param longitude
     *            Longitude in WGS84 geodetic coordinates -- positive is north.
     * @param hae
     *            Altitude in WGS84 geodetic coordinates, in meters (HAE).
     * @return the Horizontal component of the field strength in nanoteslas.
     */
    public static double getHorizontalStrength(double latitude,
            double longitude, double hae) {

        final Calendar time = Calendar.getInstance();
        time.setTimeInMillis(CoordinatedTime.currentTimeMillis());

        return getHorizontalStrength(latitude, longitude, hae,
                time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DATE));
    }

    /**
     * Estimate the inclination of the magnetic field in degrees -- positive
     * means the magnetic field is rotated downwards.
     *
     * @param latitude
     *            Latitude in WGS84 geodetic coordinates -- positive is east.
     * @param longitude
     *            Longitude in WGS84 geodetic coordinates -- positive is north.
     * @param hae
     *            Altitude in WGS84 geodetic coordinates, in meters (HAE).
     * @return The inclination of the magnetic field in degrees -- positive
     * means the magnetic field is rotated downwards.
     */
    public static double getInclination(double latitude, double longitude,
            double hae) {

        final Calendar time = Calendar.getInstance();
        time.setTimeInMillis(CoordinatedTime.currentTimeMillis());

        return getInclination(latitude, longitude, hae,
                time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DATE));
    }

    /**
     * Estimate the X (northward) component of the magnetic field in nanoteslas.
     *
     * @param latitude
     *            Latitude in WGS84 geodetic coordinates -- positive is east.
     * @param longitude
     *            Longitude in WGS84 geodetic coordinates -- positive is north.
     * @param hae
     *            Altitude in WGS84 geodetic coordinates, in meters (HAE).
     * @return The X (northward) component of the magnetic field in nanoteslas.
     */
    public static double getX(double latitude, double longitude, double hae) {

        final Calendar time = Calendar.getInstance();
        time.setTimeInMillis(CoordinatedTime.currentTimeMillis());

        return getX(latitude, longitude, hae,
                time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DATE));

    }

    /**
     * Estimate the Y (eastward) component of the magnetic field in nanoteslas.
     *
     * @param latitude
     *            Latitude in WGS84 geodetic coordinates -- positive is east.
     * @param longitude
     *            Longitude in WGS84 geodetic coordinates -- positive is north.
     * @param hae
     *            Altitude in WGS84 geodetic coordinates, in meters (HAE).
     * @return The Y (eastward) component of the magnetic field in nanoteslas.
     */
    public static double getY(double latitude, double longitude, double hae) {

        final Calendar time = Calendar.getInstance();
        time.setTimeInMillis(CoordinatedTime.currentTimeMillis());

        return getY(latitude, longitude, hae,
                time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DATE));

    }

    /**
     * Estimate the Z (downward) component of the magnetic field in nanoteslas.
     *
     * @param latitude
     *            Latitude in WGS84 geodetic coordinates -- positive is east.
     * @param longitude
     *            Longitude in WGS84 geodetic coordinates -- positive is north.
     * @param hae
     *            Altitude in WGS84 geodetic coordinates, in meters (HAE).
     * @return The Z (downward) component of the magnetic field in nanoteslas.
     */
    public static double getZ(double latitude, double longitude, double hae) {

        final Calendar time = Calendar.getInstance();
        time.setTimeInMillis(CoordinatedTime.currentTimeMillis());

        return getZ(latitude, longitude, hae,
                time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DATE));

    }

    static native float getDeclination(double latitude, double longitude,
            double hae, int year, int month, int day);

    static native float getFieldStrength(double latitude, double longitude,
            double hae, int year, int month, int day);

    static native float getHorizontalStrength(double latitude, double longitude,
            double hae, int year, int month, int day);

    static native float getInclination(double latitude, double longitude,
            double hae, int year, int month, int day);

    static native float getX(double latitude, double longitude, double hae,
            int year, int month, int day);

    static native float getY(double latitude, double longitude, double hae,
            int year, int month, int day);

    static native float getZ(double latitude, double longitude, double hae,
            int year, int month, int day);
}
