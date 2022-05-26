
package com.atakmap.android.routes.elevation.service;

import com.atakmap.android.routes.elevation.model.RouteData;
import com.atakmap.android.routes.elevation.model.SegmentData;
import com.atakmap.android.routes.elevation.model.UnitConverter;

import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.UTMPoint;
import com.atakmap.map.elevation.ElevationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RouteElevationService {

    private static final String TAG = "RouteElevationService";

    static private double[] toDoubleArray(List<Double> arr) {
        final double[] res = new double[arr.size()];
        for (int i = 0; i < arr.size(); ++i) {
            res[i] = arr.get(i);
        }
        return res;
    }

    static private int[] toIntArray(List<Integer> arr) {
        final int[] res = new int[arr.size()];
        for (int i = 0; i < arr.size(); ++i) {
            res[i] = arr.get(i);
        }
        return res;
    }

    private static final double _SLOPE_THRESHOLD = 1.0 / 128.0;

    /**
     * Returns the step distance in feet for route calculation. The step distance
     * starts at 30 ft, the DTED 3 post distance. A 30 ft step distance is maintained
     * up to a maximum of 1000 steps, after which the step distance is increased to
     * maintain 1000 steps
     * @param meter The length of the route (m)
     * @return The step distance (ft)
     */
    public static int computeRelativeFrequency(double meter) {
        double feet = UnitConverter.Meter.toFeet(meter);
        double stepDistance = 30;
        int maxSteps = 1000;
        if ((feet / stepDistance) > maxSteps) {
            stepDistance = feet / maxSteps;
        }
        return (int) stepDistance;
    }

    public static double[] findControlPoints(RouteData routeData,
            GeoPoint[] cps) {
        int j = 0;
        List<Double> distance = new ArrayList<>();
        for (int i = 0; i < routeData.getDistances().length
                && cps.length > 0; i++) {
            GeoPoint p = routeData.getGeoPoints()[i].get();
            if (j < cps.length) {
                if (p.getLatitude() == cps[j].getLatitude()
                        && p.getLongitude() == cps[j].getLongitude()) {
                    distance.add(routeData.getDistances()[i]);
                    j++;
                }
            }
        }
        return toDoubleArray(distance);
    }

    public static int[] getControlPointIndices(RouteData routeData,
            GeoPoint[] cps) {
        int j = 0;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < routeData.getGeoPoints().length
                && cps.length > 0; i++) {
            GeoPoint p = routeData.getGeoPoints()[i].get();
            if (j < cps.length) {
                if (p.getLatitude() == cps[j].getLatitude()
                        && p.getLongitude() == cps[j].getLongitude()) {
                    indices.add(i);
                    j++;
                }
            }
        }
        return toIntArray(indices);
    }

    public static GeoPointMetaData closestSegmentPoint(GeoPoint source,
            GeoPoint target, GeoPoint reference) {
        UTMPoint utm1 = UTMPoint.fromGeoPoint(source);
        UTMPoint utm2 = UTMPoint.fromGeoPoint(target);
        UTMPoint utm3 = UTMPoint.fromGeoPoint(reference);
        String zoneDescriptor = utm1.getZoneDescriptor();
        // All have to be in the same zone for this to work
        if ((utm1.getZoneDescriptor() != null && !utm1.getZoneDescriptor()
                .equals(utm2.getZoneDescriptor()))
                || (utm2.getZoneDescriptor() != null && !utm2
                        .getZoneDescriptor().equals(utm3.getZoneDescriptor()))
                || (utm3.getZoneDescriptor() != null && !utm3
                        .getZoneDescriptor()
                        .equals(utm1.getZoneDescriptor()))) {
            return null;
        }
        double x1 = utm1.getEasting();
        double y1 = utm1.getNorthing();
        double x2 = utm2.getEasting();
        double y2 = utm2.getNorthing();
        double x3 = utm3.getEasting();
        double y3 = utm3.getNorthing();

        if (x1 > x2) {
            double t = x1;
            x1 = x2;
            x2 = t;
            t = y1;
            y1 = y2;
            y2 = t;

        }
        double m = (y2 - y1) / Math.abs(x2 - x1);
        double nm = -1 / m;
        double x4 = x2;
        double y4 = nm * (x4 - x3) + y3;

        double px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2)
                * (x3 * y4 - y3 * x4))
                / ((x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4));
        double py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2)
                * (x3 * y4 - y3 * x4))
                / ((x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4));
        UTMPoint p = new UTMPoint(zoneDescriptor, px, py);

        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);

        UTMPoint closest;
        if (px >= minX && maxX <= x2 && py >= minY && py <= maxY)
            closest = p;
        else if (px < minX)
            closest = new UTMPoint(zoneDescriptor, x1, y1);
        else if (px > maxX)
            closest = new UTMPoint(zoneDescriptor, x2, y2);
        else
            return null;

        return ElevationManager.getElevationMetadata(closest.toGeoPoint());
    }

    public static SegmentData expandSegment(final GeoPointMetaData source,
            final GeoPointMetaData target,
            final double startingDistance, final int incrementInFeet,
            final boolean bInterpolateAltitudes) {
        List<Double> distVec = new ArrayList<>();
        List<GeoPointMetaData> geoPointVec = new ArrayList<>();

        final double incrementInMeters = UnitConverter.Feet
                .toMeter(incrementInFeet);

        double i = startingDistance + incrementInFeet;

        // if the altitude of the source point and the altitude of the 
        // destination point are unknown, go ahead and try to look them
        // up using the locally installed DTED.

        GeoPointMetaData sourceAlt = source;
        GeoPointMetaData targAlt = target;

        // Get source altitude in HAE and store in new source point
        if (!sourceAlt.get().isAltitudeValid())
            sourceAlt = ElevationManager.getElevationMetadata(source.get());

        GeoPointMetaData newSource = GeoPointMetaData.wrap(
                new GeoPoint(source.get().getLatitude(),
                        source.get().getLongitude(),
                        sourceAlt.get().getAltitude(),
                        source.get().getCE(),
                        source.get().getLE()),
                source.getGeopointSource(), sourceAlt.getAltitudeSource());

        // Get target altitude in HAE and store in new target point
        if (!target.get().isAltitudeValid())
            targAlt = ElevationManager.getElevationMetadata(target.get());

        GeoPointMetaData newTarget = GeoPointMetaData.wrap(
                new GeoPoint(target.get().getLatitude(),
                        target.get().getLongitude(),
                        targAlt.get().getAltitude(),
                        target.get().getCE(),
                        target.get().getLE()),
                target.getGeopointSource(), targAlt.getAltitudeSource());

        // Add starting point data
        geoPointVec.add(newSource);
        distVec.add(startingDistance);

        // Calculate total distance and altitude delta
        double totalDistance = GeoCalculations.distanceTo(newSource.get(),
                newTarget.get());
        Double totalAltChange = null;
        if (sourceAlt.get().isAltitudeValid()
                && targAlt.get().isAltitudeValid())
            totalAltChange = targAlt.get().getAltitude()
                    - sourceAlt.get().getAltitude();

        if (totalDistance > 0 && totalDistance > incrementInMeters) {
            // Find altitudes between source and target points
            GeoPoint newPoint;
            double currentDistance = 0;
            do {
                double bearing = DistanceCalculations
                        .bearingFromSourceToTarget(
                                newSource.get(), newTarget.get());
                newPoint = DistanceCalculations.metersFromAtBearing(
                        newSource.get(), incrementInMeters, bearing);

                /**
                 * roll in the altitude, but only if a valid altitude is found.
                 */
                double alt = GeoPoint.UNKNOWN;
                //see if we can interpolate
                if (bInterpolateAltitudes && totalAltChange != null) {
                    //Log.d(TAG, "Interpolating alt");
                    currentDistance += GeoCalculations
                            .distanceTo(newSource.get(), newPoint);
                    double currentDistanceFraction = currentDistance
                            / totalDistance;
                    alt = sourceAlt.get().getAltitude()
                            + (totalAltChange * currentDistanceFraction);

                }

                //see if we can get alt from DTED (HAE)
                if (!GeoPoint.isAltitudeValid(alt)) {
                    //Log.d(TAG, "Using DTED alt");
                    alt = ElevationManager.getElevation(newPoint, null);
                }

                if (GeoPoint.isAltitudeValid(alt)) {
                    newPoint = new GeoPoint(newPoint.getLatitude(),
                            newPoint.getLongitude(),
                            alt);
                }

                geoPointVec.add(GeoPointMetaData.wrap(newPoint));
                distVec.add(i);
                i += incrementInFeet;
                newSource = GeoPointMetaData.wrap(newPoint);
            } while (GeoCalculations.distanceTo(newPoint,
                    newTarget.get()) > incrementInMeters);

            // adjust i, it's more than increment count
            i -= incrementInFeet
                    - UnitConverter.Meter.toFeet(GeoCalculations
                            .distanceTo(newPoint, newTarget.get()));
        } else {
            // adjust i, it's more than increment count
            i -= incrementInFeet
                    - UnitConverter.Meter.toFeet(GeoCalculations
                            .distanceTo(newSource.get(), newTarget.get()));
        }

        geoPointVec.add(newTarget);
        distVec.add(i);

        SegmentData data = new SegmentData();
        data.setDistances(toDoubleArray(distVec));
        data.setGeoPoints(
                geoPointVec.toArray(new GeoPointMetaData[0]));
        data.setTotalDistance(i);

        return data;
    }

    public static RouteData expandRoute(final GeoPointMetaData[] route,
            final int incrementInFeet, boolean bInterpolateAltitudes) {
        List<Double> distVec = new ArrayList<>();
        List<GeoPointMetaData> geoPointVec = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        double startingDistance = 0.0;
        indices.add(0);
        for (int i = 1; i < route.length; i++) {
            SegmentData result = RouteElevationService.expandSegment(
                    route[i - 1], route[i],
                    startingDistance, incrementInFeet, bInterpolateAltitudes);
            geoPointVec.addAll(Arrays.asList(result.getGeoPoints()));

            double[] distances = result.getDistances();
            for (double distance : distances) {
                distVec.add(distance);
            }

            startingDistance = result.getTotalDistance();
            indices.add(distVec.size() - ((route.length - 1 == i) ? 1 : 0));
        }
        RouteData data = new RouteData();
        data.setGeoPoints(
                geoPointVec.toArray(new GeoPointMetaData[0]));
        data.setDistances(toDoubleArray(distVec));
        data.setIndices(indices.toArray(new Integer[0]));
        data.setTotalDistance(startingDistance);
        data.setUnexpandedGeoPoints(route);
        data.setInterpolatedAltitudes(bInterpolateAltitudes);
        return data;
    }

    public static RouteData compressDataset(RouteData input) {
        List<Double> distVec = new ArrayList<>();
        List<GeoPointMetaData> geoVec = new ArrayList<>();
        List<Integer> cps = new ArrayList<>();

        int c = 0;

        if (input.getGeoPoints().length > 3) {
            distVec.add(input.getDistances()[0]);
            geoVec.add(input.getGeoPoints()[0]);

            for (int i = 0; i < input.getGeoPoints().length - 2; i++) {
                Double x_s = input.getDistances()[i];
                Double x_c = input.getDistances()[i + 1];
                Double x_f = input.getDistances()[i + 2];

                Double y_s = input.getGeoPoints()[i].get().getAltitude();
                Double y_c = input.getGeoPoints()[i + 1].get().getAltitude();
                Double y_f = input.getGeoPoints()[i + 2].get().getAltitude();

                Double s_f = (y_f - y_s) / (x_f - x_s);
                Double s_c = (y_c - y_s) / (x_c - x_s);

                if (c < input.getControlPointData().getIndices().length
                        && i == input.getControlPointData().getIndices()[c]) {
                    cps.add(distVec.size());
                    c++;
                }

                // add start point
                distVec.add(x_s);
                geoVec.add(input.getGeoPoints()[i]);

                // determine if we should include
                if (Math.abs(s_f - s_c) < _SLOPE_THRESHOLD
                        && c < input.getControlPointData().getIndices().length
                        && input.getControlPointData().getIndices()[c] != i
                                + 1) {
                    i++; // skip
                }
            }

            cps.add(distVec.size());
            distVec.add(input.getDistances()[input.getDistances().length - 1]);
            geoVec.add(input.getGeoPoints()[input.getGeoPoints().length - 1]);
        } else {
            return input;
        }

        RouteData data = input.copy();
        data.setGeoPoints(geoVec.toArray(new GeoPointMetaData[0]));
        data.setDistances(toDoubleArray(distVec));
        data.getControlPointData().setIndices(toIntArray(cps));
        return data;
    }
}
