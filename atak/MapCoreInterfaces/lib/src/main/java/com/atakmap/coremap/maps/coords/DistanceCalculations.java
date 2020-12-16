
package com.atakmap.coremap.maps.coords;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.maps.conversion.EGM96;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.sin;

/**
 * Utility functions to compute distances on the Earth.
 * @deprecated use {@link GeoCalculations} or instance methods on
 *             {@link GeoPoint}
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public final class DistanceCalculations {

    private static final String TAG = "DistanceCalculations";

    /**
     * Static methods only.
     */
    private DistanceCalculations() {
        // static only
    }

    /**
     * Compute a point given a starting point, distance away and direction. The resulting point will
     * have no elevation data.
     * 
     * @param start starting point
     * @param distance distance away from starting point in meters
     * @param bearing direction away from starting point in degrees from North
     * @return calculated point without elevation.  If elevation is desired, you need to 
     * call computeDestinationPoint(GeoPoint, double, double, double) 
     *
     * @deprecated use {@link #computeDestinationPoint(GeoPoint, double, double)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static GeoPoint metersFromAtBearing(GeoPoint start, double distance,
            double bearing) {
        final GeoPoint gp = computeDestinationPoint(start, bearing, distance,
                0.0d);
        return new GeoPoint(gp.getLatitude(), gp.getLongitude(),
                GeoPoint.UNKNOWN);
    }

    /**
     * See <code>GeoPoint.distanceTo</code>
     * Note, this method does _not_ account for difference in elevation
     *
     * @param source GeoPoint denoting the source location
     * @param target GeoPoint denoting the target location
     * @return a double containing the calculated distance in meters
     *
     * @deprecated use {@link GeoCalculations#distanceTo(GeoPoint, GeoPoint)}
     */
    public static double metersFromAtSourceTarget(GeoPoint source,
            GeoPoint target) {
        return calculateRange(source, target);
    }

    /**
     * Returns the angle about North in degrees to the target from the source.
     * 
     * @param source
     * @param target
     * @return angle in degrees
     *
     * @deprecated use {@link GeoCalculations#bearingTo(GeoPoint, GeoPoint)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static double bearingFromSourceToTarget(GeoPoint source,
            GeoPoint target) {
        double val = source.bearingTo(target);

        if (val < 0)
            val = (val % 360) + 360d;

        if (val > 360)
            val = val % 360;

        return val;
    }

    public static double calculateAngleDifference(double init, double fina) {
        double angle = fina - init;
        angle += angle > 180 ? -360
                : angle < -180 ? 360
                        : 0;
        return angle;
    }

    /**
     * ^third / \ / \ [0]/ \[2] / \ first/_________\second [1]
     */
    public static double[] calculateTriangleLengths(GeoPoint first,
            GeoPoint second, GeoPoint third) {
        double[] lengths = new double[3];
        lengths[0] = calculateRange(first, third);
        lengths[1] = calculateRange(first, second);
        lengths[2] = calculateRange(third, second);
        return lengths;
    }

    /**
     * ^ /|\ / | \ a/ | \c / |H \ /____|____\ b
     */
    public static double calculateTriangleHeight(double a, double b, double c) {
        double s = (a + b + c) / 2;
        return (2 * Math.sqrt(s * (s - a) * (s - b) * (s - c))) / b;
    }

    /**
     * Computes a rotation of a given yxcoordinates and a angle center point pair that
     * is the resultant GeoPoints without any altitude values.
     * @param refPoint the reference point to rotate around.
     * @param yxcoords the yx coordinates to use for rotation.
     * @param angle the angle by which to rotate.
     */
    public static GeoPoint[] rotateTransformation(GeoPoint refPoint,
            double[][] yxcoords, double angle) {

        GeoPoint[] corners = new GeoPoint[yxcoords.length];

        double angRad = Math.toRadians(angle);

        MutableUTMPoint centerUTM = new MutableUTMPoint(refPoint.getLatitude(),
                refPoint.getLongitude());

        for (int i = 0; i < yxcoords.length; i++) {
            double rotatedX = Math.cos(angRad) * yxcoords[i][1]
                    - sin(angRad) * yxcoords[i][0];
            double rotatedY = sin(angRad) * yxcoords[i][1]
                    + Math.cos(angRad) * yxcoords[i][0];

            centerUTM.offset(rotatedX, rotatedY);
            double[] cor1ll = centerUTM.toLatLng(null);
            corners[i] = new GeoPoint(cor1ll[0], cor1ll[1]);
            centerUTM.offset(-rotatedX, -rotatedY);
        }
        return corners;
    }

    /**
     * Computes the centroid of a closed polygon, if vertices overlap the center point will be off.
     * 
     * @param points providing an array of GeoPoint describing the shape.
     * @return GeoPoint center of closed polygon, discarding altitude.
     */
    public static GeoPoint computeCenter(final GeoPoint[] points) {
        return GeoCalculations.computeAverage(points, false);
    }

    /**
     * @deprecated use {@link GeoCalculations#bearingTo(GeoPoint, GeoPoint)}; downstream consistency
     *              may require subtracting <code>-90d</code> from the result and negating
     * @param startPoint
     * @param endPoint
     * @return
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static double calculateBearing(final GeoPoint startPoint,
            final GeoPoint endPoint) {

        double bearing = startPoint.bearingTo(endPoint) - 90;

        // Convert to azimuth
        if (bearing < 0)
            bearing = bearing + 360;

        return -bearing;
    }

    /**
     * Bring a bearing between [-180..180]
     * @param bearing the bearing.
     * @return A value that might be between -180..180.
     */
    public static double normalizeBearing(double bearing) {
        if (bearing > 180) {
            return bearing - 360;
        } else if (bearing < -180) {
            return bearing + 360;
        }
        return bearing;
    }

    /**
     * See <code>GeoPoint.distanceTo</code>
     * Note, this method does _not_ account for difference in elevation
     *
     * @param startPoint
     * @param endPoint
     * @return distance in meters
     *
     * @deprecated use {@link GeoCalculations#distanceTo(GeoPoint, GeoPoint)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static double calculateRange(final GeoPoint startPoint,
            final GeoPoint endPoint) {
        return GeoCalculations.distanceTo(startPoint, endPoint);
    }

    /**
     * given points at different elevations, find the Slant Range between the 2 points. Basically
     * this returns the hypotenuse of the right triangle created by the two points.
     * 
     * @param startPoint
     * @param endPoint
     * @return - the slant range in meters between the 2 points. If one or both points does not have
     *         a valid elevation, return Double.NaN
     */
    public static double calculateSlantRange(GeoPoint startPoint,
            GeoPoint endPoint) {
        if (!startPoint.isAltitudeValid()
                || !endPoint.isAltitudeValid())
            return Double.NaN;

        return GeoCalculations.distance(startPoint.getLatitude(),
                startPoint.getLongitude(),
                EGM96.getHAE(startPoint),
                endPoint.getLatitude(),
                endPoint.getLongitude(),
                EGM96.getHAE(endPoint),
                GeoCalculations.CALC_SLANT);
    }

    /**
     * Returns the angular distances across a curved surface using haversine algorithm.
     * @param lat1deg the latitude in degrees
     * @param lon1deg the longitude in degrees
     * @param lat2deg the latitude in degrees
     * @param lon2deg the longitude in degrees
     */
    public static double haversine(final double lat1deg, final double lon1deg,
            final double lat2deg, final double lon2deg) {

        return GeoCalculations.distance(lat1deg, lon1deg, 0d, lat2deg, lon2deg,
                0d, GeoCalculations.CALC_QUICK);
    }

    /**
     * Returns an angle between -{@linkplain Math#PI PI} and {@linkplain Math#PI PI} equivalent to
     * the specified angle in radians.
     * 
     * @param alpha An angle value in radians.
     * @return The angle between between -{@linkplain Math#PI PI} and {@linkplain Math#PI PI}.
     */
    private static double castToAngleRange(final double alpha) {
        return alpha - (2 * PI) * floor(alpha / (2 * PI) + 0.5);
    }

    // /////////////////////////////////////////////////////////////
    // ////// ////////
    // ////// G E O D E T I C M E T H O D S ////////
    // ////// ////////
    // /////////////////////////////////////////////////////////////

    /**
     * Computes the destination point from the {@linkplain GeoPoint starting point}, given an
     * azimuth and distance in the direction of the destination point.
     *
     * <P>The <code>distance</code> parameter ALWAYS represents surface distance, whether or not
     * the <code>inclination</code> is <code>NAN</code>. This means that for computing slant points,
     * <code>distance</code> does not equal the <I>slant range</I>.
     * 
     * @param point Starting point of the calculation.
     * @param a Azimuth in degrees (True North)
     * @param distance Surface distance between the start and destination point in meters
     * @param inclination from start point, <code>NAN</code> for surface only computation
     * @return Destination point, if the point is antipodal from the starting point then the
     *         calculation could be off. Attempts to calculate the altitude from inclination.
     */
    public static GeoPoint computeDestinationPoint(GeoPoint point, double a,
            double distance, double inclination) {

        final GeoPoint result = computeDestinationPoint(point, a, distance);
        if (!Double.isNaN(inclination)) {
            double elevation = Math.tan(inclination
                    * ConversionFactors.RADIANS_TO_DEGREES)
                    * distance;

            if (point.isAltitudeValid()) {
                double start_altitude = point.getAltitude();
                double alt = (elevation + start_altitude);

                return new GeoPoint(result.getLatitude(), result.getLongitude(),
                        alt);
            }
        }

        return result;
    }

    /**
     * Computes the destination point from the {@linkplain GeoPoint starting point}, given an
     * azimuth and distance in the direction of the destination point.
     * 
     * @param point Starting point of the calculation.
     * @param a Azimuth in degrees (True North)
     * @param distance Distance in meters
     * @return Destination point, if the point is antipodal from the starting point then the
     *         calculation could be off. Discards the altitude.
     */

    public static GeoPoint computeDestinationPoint(GeoPoint point, double a,
            double distance) {
        return GeoCalculations.pointAtDistance(point, a, distance);
    }

    /**
     * Calculates the meridian arc length between two points in the same meridian in the referenced
     * ellipsoid.
     * 
     * @param latitude1 The latitude of the first point (in decimal degrees).
     * @param latitude2 The latitude of the second point (in decimal degrees).
     * @return Returned the meridian arc length between latitude1 and latitude2
     */
    public static double getMeridianArcLength(final double latitude1,
            final double latitude2) {
        return getMeridianArcLengthRadians(latitude1, latitude2);
    }

    /**
     * Calculates the meridian arc length between two points in the same meridian in the referenced
     * ellipsoid.
     * 
     * @param P1 The latitude of the first point (in radians).
     * @param P2 The latitude of the second point (in radians).
     * @return Returned the meridian arc length between P1 and P2
     */
    private static double getMeridianArcLengthRadians(final double P1,
            final double P2) {
        /*
         * Latitudes P1 and P2 in radians positive North and East. Forward azimuths at both points
         * returned in radians from North. Source: org.geotools.referencing.GeodeticCalcultor see
         * http://geotools.org
         */
        double S1 = abs(P1);
        double S2 = abs(P2);
        double DA = (P2 - P1);
        // Check for a 90 degree lookup
        if (S1 > TOLERANCE_0 || S2 <= (PI / 2 - TOLERANCE_0)
                || S2 >= (PI / 2 + TOLERANCE_0)) {
            final double DB = sin(P2 * 2.0) - sin(P1 * 2.0);
            final double DC = sin(P2 * 4.0) - sin(P1 * 4.0);
            final double DD = sin(P2 * 6.0) - sin(P1 * 6.0);
            final double DE = sin(P2 * 8.0) - sin(P1 * 8.0);
            final double DF = sin(P2 * 10.0) - sin(P1 * 10.0);
            // Compute the S2 part of the series expansion
            S2 = -DB * B / 2.0 + DC * C / 4.0 - DD * D / 6.0 + DE * E / 8.0
                    - DF * F / 10.0;
        } else {
            S2 = 0;
        }
        // Compute the S1 part of the series expansion
        S1 = DA * A;
        // Compute the arc length
        return abs(semiMajorAxis * (1.0 - eccentricitySquared) * (S1 + S2));
    }

    /**
     * Converts the angle <var>alpha</var> into a Heading from True North.
     * 
     * @param alpha Angle in range of -PI to PI
     * @return Heading in range of 0 to 360
     */
    private static double convertAngleToHeading(double alpha) {

        if (alpha < 0) {
            return alpha + 2 * PI;
        } else {
            return alpha;
        }
    }

    /**
     * Computes the azimuth from True North and distance (m) from {@linkplain GeoPoint a} to
     * {@linkplain GeoPoint b} using the WGS 84 Ellipsoid.
     * 
     * @return {double distance, double azimuth}
     */
    public static double[] computeDirection(GeoPoint a, GeoPoint b) {
        double[] retval = new double[2];
        retval[0] = GeoCalculations.distance(a.getLatitude(), a.getLongitude(),
                0d, b.getLatitude(), b.getLongitude(), 0d, 0);
        retval[1] = GeoCalculations.bearing(a.getLatitude(), a.getLongitude(),
                b.getLatitude(), b.getLongitude(), 0);
        return retval;
    }

    /**
     * Tolerance factors from the strictest (<code>TOLERANCE_0</CODE>) to the most relax one (
     * <code>TOLERANCE_3</CODE>).
     */
    private static final double TOLERANCE_0 = 5.0e-15, // tol0
            TOLERANCE_1 = 5.0e-14, // tol1
            TOLERANCE_2 = 5.0e-13, // tt
            TOLERANCE_3 = 7.0e-3; // tol2

    /**
     * The encapsulated ellipsoid.
     */
    private final static Ellipsoid ellipsoid;

    /*
     * The semi major axis of the refereced ellipsoid.
     */
    private static final double semiMajorAxis;

    /*
     * The semi minor axis of the refereced ellipsoid.
     */
    private final static double semiMinorAxis;

    /*
     * The eccenticity squared of the refereced ellipsoid.
     */
    private final static double eccentricitySquared;

    /**
     * GPNARC parameters computed from the ellipsoid.
     */
    private final static double A, B, C, D, E, F;

    /**
     * GPNHRI parameters computed from the ellipsoid. {@code f} if the flattening of the referenced
     * ellipsoid. {@code f2}, {@code f3} and {@code f4} are <var>f<sup>2</sup></var>,
     * <var>f<sup>3</sup></var> and <var>f<sup>4</sup></var> respectively.
     */
    private final static double f, f2, f3, f4;

    /**
     * Parameters computed from the ellipsoid.
     */
    private static final double a01;

    /**
     * Statically compute all of the associated values from the WGS 84 Ellipsoid
     */
    static {
        ellipsoid = Ellipsoid.WGS_84;
        semiMajorAxis = ellipsoid.getSemiMajorAxis();
        semiMinorAxis = ellipsoid.getSemiMinorAxis();

        /* calculation of GPNHRI parameters */
        f = (semiMajorAxis - semiMinorAxis) / semiMajorAxis;
        f2 = f * f;
        f3 = f * f2;
        f4 = f * f3;
        eccentricitySquared = f * (2.0 - f);

        /* Calculation of GNPARC parameters */
        final double E2 = eccentricitySquared;
        final double E4 = E2 * E2;
        final double E6 = E4 * E2;
        final double E8 = E6 * E2;
        final double EX = E8 * E2;

        A = 1.0 + 0.75 * E2 + 0.703125 * E4 + 0.68359375 * E6
                + 0.67291259765625 * E8 + 0.6661834716796875 * EX;
        B = 0.75 * E2 + 0.9375 * E4 + 1.025390625 * E6 + 1.07666015625 * E8
                + 1.1103057861328125 * EX;
        C = 0.234375 * E4 + 0.41015625 * E6 + 0.538330078125 * E8
                + 0.63446044921875 * EX;
        D = 0.068359375 * E6 + 0.15380859375 * E8 + 0.23792266845703125 * EX;
        E = 0.01922607421875 * E8 + 0.0528717041015625 * EX;
        F = 0.00528717041015625 * EX;

        final double a = f3 * (1.0 + 2.25 * f);
        a01 = -f2 * (1.0 + f + f2) / 4.0;
    }

}
