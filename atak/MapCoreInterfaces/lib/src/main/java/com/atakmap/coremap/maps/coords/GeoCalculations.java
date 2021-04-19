
package com.atakmap.coremap.maps.coords;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.conversion.EGM96;

/**
 * Landing zone for all of the static methods within GeoPoint.
 */
public final class GeoCalculations {
    public final static int HEMISPHERE_EAST = 0;
    public final static int HEMISPHERE_WEST = 1;

    final static int CALC_SLANT = 0x02;
    final static int CALC_QUICK = 0x01;

    private GeoCalculations() {
    }

    /**
     * Given a GeoPoint return an integer that describes it is in HEMISPHERE_WEST or HEMISPHERE_EAST.
     * @param gp the GeoPoint to test
     * @return the integer describing which hemisphere.
     */
    public static int getHemisphere(GeoPoint gp) {
        return gp.getLongitude() < 0d ? HEMISPHERE_WEST : HEMISPHERE_EAST;
    }

    /**
     * Given a GeoPoint return a GeoPoint that is wrapped to the appropriate hemisphere.
     * @param gp a GeoPoint
     * @param toHemi the integer flag designating HEMISPHERE_WEST or HEMISPHERE_EAST
     * @return the GeoPoint with the longitude wrapped to the correct hemisphere, more positive is
     * east and more negative is west.
     */
    public static GeoPoint wrapLongitude(GeoPoint gp, int toHemi) {
        int fromHemi = getHemisphere(gp);
        if (fromHemi == toHemi)
            return gp;
        double lng = gp.getLongitude();
        if (fromHemi == HEMISPHERE_WEST)
            lng += 360d;
        else if (fromHemi == HEMISPHERE_EAST)
            lng -= 360d;
        return new GeoPoint(gp.getLatitude(), lng, gp.getAltitude(),
                gp.getAltitudeReference(), gp.getCE(),
                gp.getLE());
    }

    /**
     * @param longitude
     * @return
     */
    public static double wrapLongitude(double longitude) {
        if (longitude < -180d)
            return longitude + 360;
        else if (longitude > 180d)
            return longitude - 360;
        return longitude;
    }

    /**
     * Given two lines described by points {start0, end0} and {start1, end1}, find the intersection
     * @param start0 the start point of line 0
     * @param end0 the end point of line 0
     * @param start1 the start point of line 1
     * @param end1 the end point of line 1
     * @return return the geopoint formed by the intersection of these lines
     */
    public static GeoPoint findIntersection(GeoPoint start0, GeoPoint end0,
            GeoPoint start1,
            GeoPoint end1) {
        double x1 = start0.getLongitude(), y1 = start0.getLatitude();
        double x2 = end0.getLongitude(), y2 = end0.getLatitude();
        double x3 = start1.getLongitude(), y3 = start1.getLatitude();
        double x4 = end1.getLongitude(), y4 = end1.getLatitude();

        double mua, mub;
        double denom, numera, numerb;

        final double EPS = 0.0000001;

        denom = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1);
        numera = (x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3);
        numerb = (x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3);

        /* Are the line coincident? */
        if (Math.abs(numera) < EPS && Math.abs(numerb) < EPS
                && Math.abs(denom) < EPS) {
            return new GeoPoint((y1 + y2) / 2,
                    (x1 + x2) / 2);

        }

        /* Are the line parallel */
        if (Math.abs(denom) < EPS) {
            return null;
        }

        /* Is the intersection along the segments */
        mua = numera / denom;
        mub = numerb / denom;
        if (mua < 0 || mua > 1 || mub < 0 || mub > 1) {
            return null;
        }

        return new GeoPoint(y1 + mua * (y2 - y1),
                x1 + mua * (x2 - x1));

    }

    /** @deprecated use {@link #midPointCartesian(GeoPoint, GeoPoint, boolean)} */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static GeoPoint midPoint(GeoPoint a, GeoPoint b, boolean wrap180) {
        return midPointCartesian(a, b, wrap180);
    }

    /**
     * Deternine the midpoint between two points in carteasian space
     * @param a the first point
     * @param b the second point
     * @param wrap180 if the longitude should be wrapped to be within -180 to 180
     * @return the carteasian center point
     */
    public static GeoPoint midPointCartesian(GeoPoint a, GeoPoint b,
            boolean wrap180) {
        GeoPoint res = null;
        if (a == null && b == null) {
            // return res;
            // both are invalid - do nothing
        } else if (a == null) {
            res = b;
        } else if (b == null) {
            res = a;
        } else {
            if (wrap180 && Math.abs(a.getLongitude() - b.getLongitude()) > 180)
                b = wrapLongitude(b, getHemisphere(a));
            res = new GeoPoint((a.getLatitude() + b.getLatitude()) / 2d,
                    (a.getLongitude() + b.getLongitude()) / 2d);
        }
        return res;
    }

    /**
     * Given a source and a destination compute the bearing.
     * @param source the source point
     * @param destination the destination point
     * @return the bearing in degrees
     */
    public static double bearingTo(final GeoPoint source,
            final GeoPoint destination) {

        double retval = bearing(source.getLatitude(), source.getLongitude(),
                destination.getLatitude(), destination.getLongitude(), 0);
        if (retval < 0)
            retval += 360;
        return retval;
    }

    /**
     * Given an array of points compute the average point
     * @param points the array of points
     * @param wrap180 true if the value should be wrapped to be within -180..180
     * @return the point that is the average.
     */
    public static GeoPoint computeAverage(GeoPoint[] points, boolean wrap180) {
        return computeAverage(points, 0, points.length, wrap180);
    }

    /**
     * Given an array of points compute the average point
     * @param points the array of points
     * @return the point that is the average.
     */
    public static GeoPoint computeAverage(GeoPoint[] points) {
        return computeAverage(points, false);
    }

    /**
     * Determine if an array of points crosses the IDL.
     * @param points the array of points
     * @param offset the offset within the array
     * @param count the number of points to consider from the offset
     * @return true if the designated points cross the IDL
     */
    public static boolean crossesIDL(GeoPoint[] points, int offset, int count) {
        count = Math.min(count, points.length - offset);
        if (count <= 0)
            return false;

        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        for (int i = offset; i < offset + count; i++) {
            GeoPoint p = points[i];
            if (p.getLongitude() < minLng)
                minLng = p.getLongitude();
            if (p.getLongitude() > maxLng)
                maxLng = p.getLongitude();
        }
        if (minLng < -180 || maxLng > 180)
            return true;

        // The problem here is that we don't know if the group of points is
        // supposed to wrap over the IDL or cover most of the planet
        // For now just assume the smaller span is correct
        return maxLng - minLng > 180;
    }

    /**
     * Determine if an array of points crosses the IDL.
     * @param points the array of points
     * @param offset the offset within the array
     * @param count the number of points to consider from the offset
     * @return true if the designated points cross the IDL
     */
    public static boolean crossesIDL(GeoPointMetaData[] points, int offset,
            int count) {
        count = Math.min(count, points.length - offset);
        if (count <= 0)
            return false;

        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        for (int i = offset; i < offset + count; i++) {
            GeoPoint p = points[i].get();
            if (p.getLongitude() < minLng)
                minLng = p.getLongitude();
            if (p.getLongitude() > maxLng)
                maxLng = p.getLongitude();
        }
        if (minLng < -180 || maxLng > 180)
            return true;

        // The problem here is that we don't know if the group of points is
        // supposed to wrap over the IDL or cover most of the planet
        // For now just assume the smaller span is correct
        return maxLng - minLng > 180;
    }

    /**
     * Determine if an array of points crosses the IDL.
     * @param points the array of points
     * @return true if the designated points cross the IDL
     */
    public static boolean crossesIDL(GeoPoint[] points) {
        return crossesIDL(points, 0, points.length);
    }

    /**
     * Find the point that is the average in an array of points
     * @param points the array of points
     * @param offset the offset into the array of points
     * @param count the number of points from the offset to consider
     * @return the average point
     */
    public static GeoPoint computeAverage(GeoPoint[] points, int offset,
            int count) {
        return computeAverage(points, offset, count, false);
    }

    /**
     * Find the point that is the average in an array of points
     * @param points the array of points
     * @param offset the offset into the array of points
     * @param count the number of points from the offset to consider
     * @param wrap180 if it is intended that these points will consider wrapping the IDL (continuous scrolling)
     * @return the average point
     */
    public static GeoPoint computeAverage(GeoPoint[] points, int offset,
            int count, boolean wrap180) {
        if (wrap180)
            wrap180 = crossesIDL(points, offset, count);

        count = Math.min(count, points.length - offset);
        if (count <= 0)
            return GeoPoint.ZERO_POINT;

        int hemi = -1;
        double avgLat = 0;
        double avgLong = 0;
        double div_size = 1d / count;
        for (int i = offset; i < offset + count; i++) {
            GeoPoint p = points[i];
            if (wrap180) {
                if (hemi == -1)
                    hemi = getHemisphere(p);
                else
                    p = wrapLongitude(p, hemi);
            }
            avgLat += p.getLatitude() * div_size;
            avgLong += p.getLongitude() * div_size;
        }
        if (wrap180)
            avgLong = wrapLongitude(avgLong);
        return new GeoPoint(avgLat, avgLong);
    }

    /**
     * Find the point that is the center of the extremes witihn an array of points
     * @param points the array of points
     * @param offset the offset into the array of points
     * @param count the number of points from the offset to consider
     * @return the center point
     */
    public static GeoPoint centerOfExtremes(GeoPoint[] points, int offset,
            int count) {
        return centerOfExtremes(points, offset, count, false);
    }

    /**
     * Find the point that is the center of the extremes witihn an array of points
     * @param points the array of points
     * @param offset the offset into the array of points
     * @param count the number of points from the offset to consider
     * @return the center point
     */
    public static GeoPoint centerOfExtremes(GeoPointMetaData[] points,
            int offset,
            int count) {
        return centerOfExtremes(points, offset, count, false);
    }

    /**
     * Find the point that is the center of the extremes witihn an array of points
     * @param points the array of points
     * @param offset the offset into the array of points
     * @param count the number of points from the offset to consider
     * @param wrap180 if it is intended that these points will consider wrapping the IDL (continuous scrolling)
     * @return the center point
     */
    public static GeoPoint centerOfExtremes(GeoPoint[] points, int offset,
            int count, boolean wrap180) {
        GeoPoint pt = null;
        if (points != null && points.length > 0) {
            int[] e = findExtremes(points, offset, count);

            double latSpan = points[e[1]].getLatitude()
                    - points[e[3]].getLatitude();
            double lonSpan = points[e[2]].getLongitude()
                    - points[e[0]].getLongitude();
            if (wrap180 && lonSpan > 180)
                lonSpan -= 360;
            double lng = points[e[0]].getLongitude() + (lonSpan / 2);
            if (wrap180)
                lng = wrapLongitude(lng);
            pt = new GeoPoint(points[e[3]].getLatitude() + (latSpan / 2), lng);
        }
        return pt;
    }

    /**
     * Find the point that is the center of the extremes witihn an array of points
     * @param points the array of points
     * @param offset the offset into the array of points
     * @param count the number of points from the offset to consider
     * @param wrap180 if it is intended that these points will consider wrapping the IDL (continuous scrolling)
     * @return the center point
     */
    public static GeoPoint centerOfExtremes(GeoPointMetaData[] points,
            int offset,
            int count, boolean wrap180) {
        GeoPoint pt = null;
        if (points != null && points.length > 0) {
            int[] e = findExtremes(points, offset, count, false);

            double latSpan = points[e[1]].get().getLatitude()
                    - points[e[3]].get().getLatitude();
            double lonSpan = points[e[2]].get().getLongitude()
                    - points[e[0]].get().getLongitude();
            if (wrap180 && lonSpan > 180)
                lonSpan -= 360;
            double lng = points[e[0]].get().getLongitude() + (lonSpan / 2);
            if (wrap180)
                lng = wrapLongitude(lng);
            pt = new GeoPoint(points[e[3]].get().getLatitude() + (latSpan / 2),
                    lng);
        }
        return pt;
    }

    public static int[] findExtremes(GeoPoint[] points, int offset, int count) {
        return findExtremes(points, offset, count, false);
    }

    /**
     * GIven an array of points, find the outer most extremes
     * @param points the array of points
     * @param offset the offset into the array of points
     * @param count the number of points from the offset to consider
     * @param wrap180 if it is intended that these points will consider wrapping the IDL (continuous scrolling)
     * @return the index of the 4 points that define the extremes of the array of points {W, N, E, S}
     */
    public static int[] findExtremes(GeoPoint[] points, int offset, int count,
            boolean wrap180) {
        /*
         * 1 0 2 3
         */
        boolean crossesIDL = wrap180 && crossesIDL(points, offset, count);
        int[] e = new int[4];
        e[0] = e[1] = e[2] = e[3] = -1;
        double N = 0, W = 0, S = 0, E = 0;
        for (int i = offset, c = 0; c < count && i < points.length; ++i, ++c) {
            GeoPoint t = points[i];
            double lat = t.getLatitude(), lng = t.getLongitude();
            if (crossesIDL) {
                if (lng > 0)
                    lng -= 360d;
                else if (lng < 0)
                    lng += 360d;
            }
            if (c == 0) {
                N = S = lat;
                E = W = lng;
                e[0] = e[1] = e[2] = e[3] = i;
            } else {
                if (lat > N) {
                    N = lat;
                    e[1] = i;
                } else if (lat < S) {
                    S = lat;
                    e[3] = i;
                }
                if (lng > E) {
                    E = lng;
                    e[2] = i;
                } else if (lng < W) {
                    W = lng;
                    e[0] = i;
                }
            }
        }
        return e;
    }

    /**
     * GIven an array of points, find the outer most extremes
     * @param points the array of points
     * @param offset the offset into the array of points
     * @param count the number of points from the offset to consider
     * @param wrap180 if it is intended that these points will consider wrapping the IDL (continuous scrolling)
     * @return the index of the 4 points that define the extremes of the array of points {W, N, E, S}
     */
    public static int[] findExtremes(GeoPointMetaData[] points, int offset,
            int count,
            boolean wrap180) {
        /*
         * 1 0 2 3
         */
        boolean crossesIDL = wrap180 && crossesIDL(points, offset, count);
        int[] e = new int[4];
        e[0] = e[1] = e[2] = e[3] = -1;
        double N = 0, W = 0, S = 0, E = 0;
        for (int i = offset, c = 0; c < count && i < points.length; ++i, ++c) {
            GeoPoint t = points[i].get();
            double lat = t.getLatitude(), lng = t.getLongitude();
            if (crossesIDL) {
                if (lng > 0)
                    lng -= 360d;
                else if (lng < 0)
                    lng += 360d;
            }
            if (c == 0) {
                N = S = lat;
                E = W = lng;
                e[0] = e[1] = e[2] = e[3] = i;
            } else {
                if (lat > N) {
                    N = lat;
                    e[1] = i;
                } else if (lat < S) {
                    S = lat;
                    e[3] = i;
                }
                if (lng > E) {
                    E = lng;
                    e[2] = i;
                } else if (lng < W) {
                    W = lng;
                    e[0] = i;
                }
            }
        }
        return e;
    }

    /** @deprecated use {@link #midPointCartesian(GeoPoint, GeoPoint, boolean)} */
    @Deprecated
    @DeprecatedApi(since = "4.2.1", forRemoval = true, removeAt = "4.5")
    public static GeoPoint midPoint(GeoPoint a, GeoPoint b) {
        return midPointCartesian(a, b, false);
    }

    /**
     * Given two points, compute the midpoint utilizing WGS84.
     * @param a the start point
     * @param b the end point
     * @return the computed mid point
     */
    public static GeoPoint midPointWGS84(GeoPoint a, GeoPoint b) {
        if (a != null && b != null)
            return midpoint(a.getLatitude(), a.getLongitude(), a.getAltitude(),
                    b.getLatitude(), b.getLongitude(), b.getAltitude(), 0);
        else if (a == null && b == null)
            return null;
        else if (a != null)
            return a;
        else if (b != null)
            return b;
        else
            throw new IllegalStateException();
    }

    /**
     * Compute the straight line (as the crow flies) distance
     * @param start the starting point
     * @param destination the destination point
     * @return value in meters for the straight line distance
     */
    public static double distanceTo(final GeoPoint start,
            final GeoPoint destination) {
        return distance(start.getLatitude(), start.getLongitude(), 0d,
                destination.getLatitude(), destination.getLongitude(), 0d,
                0);
    }

    /**
     * Compute the slant distance to (hypotenuse)
     * @param start the starting point
     * @param destination the destination point
     * @return value in meters for the slant distance
     */
    public static double slantDistanceTo(final GeoPoint start,
            final GeoPoint destination) {
        return distance(start.getLatitude(),
                start.getLongitude(),
                EGM96.getHAE(start),
                destination.getLatitude(),
                destination.getLongitude(),
                EGM96.getHAE(destination),
                CALC_SLANT);
    }

    /**
     * Calculates a new GeoPoint at the given azimuth and distance away from the source point
     * @param src the source point
     * @param azimuth Azimuth in degrees True North
     * @param distance Meters
     * @return the point computed based on the src, azimuth and distance
     */
    public static GeoPoint pointAtDistance(GeoPoint src, double azimuth,
            double distance) {
        return pointAtDistance(src.getLatitude(), src.getLongitude(), azimuth,
                distance, 0);
    }

    /**
     * Computes the inclination from one point to another.
     * @param start the starting point
     * @param destination the destination point
     * @return the value in degrees
     */
    public static double inclinationTo(final GeoPoint start,
            final GeoPoint destination) {
        return slantAngle(start.getLatitude(),
                start.getLongitude(),
                EGM96.getHAE(start),
                destination.getLatitude(),
                destination.getLongitude(),
                EGM96.getHAE(destination),
                CALC_SLANT);
    }

    /**
     * Given a latitude, provides the approximate meters per degree at that latitude.
     * @param latitude the latitude
     * @return the approximate meters per degree for the longitude
     */
    public static double approximateMetersPerDegreeLongitude(double latitude) {
        final double rlat = Math.toRadians(latitude);
        return 111412.84 * Math.cos(rlat) - 93.5 * Math.cos(3 * rlat);
    }

    /**
     * Given a latitude, provides the approximate meters per degree at that latitude.
     * @param latitude the latitude
     * @return the approximate meters per degree for the latitude
     */
    public static double approximateMetersPerDegreeLatitude(double latitude) {
        final double rlat = Math.toRadians(latitude);
        return 111132.92 - 559.82 * Math.cos(2 * rlat)
                + 1.175 * Math.cos(4 * rlat);
    }

    static native double distance(double lat1, double lng1, double alt1,
            double lat2, double lng2, double alt2, int flags);

    static native double slantAngle(double lat1, double lng1, double alt1,
            double lat2, double lng2, double alt2, int flags);

    static native double bearing(double lat1, double lng1, double lat2,
            double lng2, int flags);

    static native GeoPoint midpoint(double lat1, double lng1, double alt1,
            double lat2, double lng2, double alt2, int flags);

    static native GeoPoint pointAtDistance(double lat, double lng,
            double azimuth, double distance, int flags);
}
