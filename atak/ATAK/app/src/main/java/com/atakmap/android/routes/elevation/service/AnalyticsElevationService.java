
package com.atakmap.android.routes.elevation.service;

import android.util.Pair;

import com.atakmap.android.routes.elevation.SeekerBarPanelPresenter;
import com.atakmap.android.routes.elevation.model.RouteData;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
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
     * @param stopIndex The point index to stop at
     * @return the total elevation of the route
     */
    public static double findRouteTotalElevation(RouteData routeData,
            boolean gain, int stopIndex) {
        if (routeData == null || routeData.getGeoPoints() == null)
            return 0;
        GeoPointMetaData[] pts = routeData.getGeoPoints();
        if (stopIndex < 0)
            stopIndex = pts.length;
        double thresh = SpanUtilities.convert(routeData.getTotalDistance(),
                Span.FOOT, Span.METER) / 1000;
        double ret = 0, avgRet = 0, remDist = thresh;
        int avgCount = 0;
        for (int i = 0; i < pts.length - 1 && i <= stopIndex; i++) {
            double y1 = EGM96.getHAE(pts[i].get());
            double y2 = EGM96.getHAE(pts[i + 1].get());
            remDist -= pts[i].get().distanceTo(pts[i + 1].get());
            if (GeoPoint.isAltitudeValid(y1) && GeoPoint.isAltitudeValid(y2)) {
                double delta = y2 - y1;
                avgRet += delta;
                avgCount++;
                if (remDist <= 0) {
                    delta = avgRet / avgCount;
                    if (gain && delta > 0 || !gain && delta < 0)
                        ret += delta;
                    avgRet = 0;
                    avgCount = 0;
                    remDist = thresh;
                }
            }
        }
        if (avgCount > 0 && (gain && avgRet > 0 || !gain && avgRet < 0))
            ret += avgRet / avgCount;
        return ret * ConversionFactors.METERS_TO_FEET * (gain ? 1 : -1);
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

            if (i == contactIndices[cp]) {
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
        double gain = findRouteTotalElevation(routeData, true, seekerIndex);
        seekerBarPanelPresenter.updateGainText(gain);
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
