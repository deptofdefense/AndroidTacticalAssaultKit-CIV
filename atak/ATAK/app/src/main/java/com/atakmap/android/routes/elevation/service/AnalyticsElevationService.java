
package com.atakmap.android.routes.elevation.service;

import android.util.Pair;

import com.atakmap.android.routes.elevation.SeekerBarPanelPresenter;
import com.atakmap.android.routes.elevation.model.RouteData;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.List;

public class AnalyticsElevationService {

    private static final String TAG = "AnalyticsElevationService";
    private static final double DISTANCE_TOLERANCE = 5.0e-15;

    static private double[] toArray(List<Double> arr) {
        final double[] res = new double[arr.size()];
        for (int i = 0; i < arr.size(); ++i) {
            res[i] = arr.get(i);
        }
        return res;
    }

    /**
     * Returns a pair that describes the minimum altitude and the maximum altitude given an array of
     * altitudes. Pair.FIRST is the minimum or ALTITUDE_UNKNOWN, Pair.SECOND is the maximum or
     * ALTITUDE_UNKNOWN.
     */
    public static Pair<GeoPointMetaData, GeoPointMetaData> findMinMax(
            final GeoPointMetaData[] geoPoints) {

        double min = GeoPoint.UNKNOWN;
        GeoPointMetaData minPt = GeoPointMetaData.wrap(GeoPoint.ZERO_POINT);
        double max = GeoPoint.UNKNOWN;
        GeoPointMetaData maxPt = GeoPointMetaData.wrap(GeoPoint.ZERO_POINT);

        for (GeoPointMetaData geoPoint : geoPoints) {

            final double current = EGM96.getHAE(geoPoint.get());

            if (!GeoPoint.isAltitudeValid(min)) {
                min = current;
                minPt = geoPoint;
                max = current;
                maxPt = geoPoint;
            }

            if (GeoPoint.isAltitudeValid(current)) {
                if (current < min) {
                    min = current;
                    minPt = geoPoint;
                } else if (current > max) {
                    max = current;
                    maxPt = geoPoint;
                }
            }

        }
        return new Pair<>(minPt, maxPt);
    }

    /**
     * Finds the summation of all of the gains or losses along a route in feet
     * @param routeData Route data
     * @param gain True to calculate gain, false to calculate loss
     * @param stopIndex The point index to stop at, if -1 use the entire length
     * @return the total elevation change either gain or loss base in feet for either gain or loss
     */
    public static double findRouteTotalElevation(RouteData routeData,
            boolean gain, int stopIndex) {
        double[] retval = findRouteTotalElevation(routeData, stopIndex);
        retval[0] *= ConversionFactors.METERS_TO_FEET;
        retval[1] *= ConversionFactors.METERS_TO_FEET;
        return retval[gain ? 0 : 1];
    }

    /**
     * Finds the summation of all of the gains or losses along a route in meters
     * @param routeData Route data
     * @param stopIndex The point index to stop at, if -1 use the entire length
     * @return the total elevation change where the array position 0 is gain and array position 0 is
     * loss.
     */
    public static double[] findRouteTotalElevation(RouteData routeData,
            int stopIndex) {

        double[] retval = new double[] {
                0, 0
        };
        final GeoPointMetaData[] pts;

        if (routeData == null || (pts = routeData.getGeoPoints()) == null)
            return retval;

        if (stopIndex < 0)
            stopIndex = pts.length;

        for (int i = 0; i < pts.length - 1 && i <= stopIndex; i++) {

            final GeoPoint p1 = pts[i].get();
            final GeoPoint p2 = pts[i + 1].get();

            final double y1 = EGM96.getHAE(p1);
            final double y2 = EGM96.getHAE(p2);
            if (!Double.isNaN(y1) && !Double.isNaN(y2)) {
                double diff = y2 - y1;
                if (diff > 0) {
                    retval[0] += diff;
                } else if (diff < 0) {
                    retval[1] -= diff;
                }
            }
        }
        return retval;
    }

    public static double[] findContactPointElevationGain(int[] contactIndices,
            GeoPointMetaData[] geoPoints) {
        List<Double> gain = new ArrayList<>();

        double g = 0;

        int cp = 0;
        // loop through the data and calculate the gain along the route between the contact points
        // do nested for loops?
        for (int i = 0; i < geoPoints.length - 1
                && contactIndices.length > 0; i++) {
            final double y1 = EGM96.getHAE(geoPoints[i].get());
            final double y2 = EGM96.getHAE(geoPoints[i + 1].get());
            if (GeoPoint.isAltitudeValid(y2) && GeoPoint.isAltitudeValid(y1)) {
                final double s = (y2 - y1);

                if (s > 0) {
                    g += (s * ConversionFactors.METERS_TO_FEET);
                }
            }

            if (cp < contactIndices.length && i == contactIndices[cp]) {
                if (i > 1)
                    gain.add(g);
                g = 0;
                cp++;
            }
        }

        gain.add(g);

        return toArray(gain);
    }

    public static void findRouteSeekElevationGain(RouteData routeData,
            int seekerIndex, SeekerBarPanelPresenter seekerBarPanelPresenter) {
        double[] retval = findRouteTotalElevation(routeData, seekerIndex);
        double val = (retval[0] - retval[1]) * ConversionFactors.METERS_TO_FEET;

        seekerBarPanelPresenter.updateGainText(val);
    }

    public static double findInstantaneousSlope(double[] distance,
            GeoPointMetaData[] geoPoints, final int i) {

        int p1 = i - 1;
        int p2 = i;
        int p3 = i + 1;

        if (i - 1 < 0) {
            p1 = p2;
        }

        if (i + 1 >= geoPoints.length) {
            p3 = p2;
        }

        double x1 = distance[p1];
        final double y1 = EGM96.getHAE(geoPoints[p1].get());

        double x2 = distance[p2];
        final double y2 = EGM96.getHAE(geoPoints[p2].get());

        double x3 = distance[p3];
        final double y3 = EGM96.getHAE(geoPoints[p3].get());

        if (!GeoPoint.isAltitudeValid(y1) || !GeoPoint.isAltitudeValid(y2)
                || !GeoPoint.isAltitudeValid(y3))
            return Double.NaN;

        double slopea = (y2 - y1) / (x2 - x1);
        double slopeb = (y3 - y2) / (x3 - x2);

        double s = (slopea + slopeb) / 2d;
        if (Double.isNaN(s))
            return Double.NaN;

        return s;
    }

    public static double findRouteMaximumSlope(double[] distance,
            GeoPointMetaData[] geoPoints) {
        double faction = 0;

        for (int i = 0; i < distance.length - 1; i++) {
            double x1 = distance[i];
            double x2 = distance[i + 1];
            double y1 = EGM96.getHAE(geoPoints[i].get());
            double y2 = EGM96.getHAE(geoPoints[i + 1].get());

            double dx = x2 - x1;
            if (GeoPoint.isAltitudeValid(y1) && GeoPoint.isAltitudeValid(y2)
                    && (Math.abs(dx) > DISTANCE_TOLERANCE)) {
                double s = (y2 - y1) / dx;
                if (!Double.isNaN(s) && Math.abs(s) > Math.abs(faction)) {
                    faction = s;
                }
            }

        }

        return faction;
    }

    public static void findClosestControlPoint(double[] distances,
            double[] cpDistances, int seeker, String[] cpNames,
            double totalDistance,
            SeekerBarPanelPresenter seekerBarPanelPresenter) {
        double threshold = totalDistance * 0.03; // 0.2 magic ratio
        seekerBarPanelPresenter.updateControlName("--");
        for (int i = 0; i < cpDistances.length; i++) {
            double d = Math.abs(distances[seeker] - cpDistances[i]);
            if (d <= threshold)
                seekerBarPanelPresenter.updateControlName(cpNames[i]);
        }
    }
}
