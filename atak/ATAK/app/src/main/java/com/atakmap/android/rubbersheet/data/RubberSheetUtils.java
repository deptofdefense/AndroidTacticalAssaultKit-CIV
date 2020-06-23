
package com.atakmap.android.rubbersheet.data;

import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.elevation.ElevationManager.QueryParameters;

public class RubberSheetUtils {

    // DTED lookup
    private static final QueryParameters DTM_FILTER = new QueryParameters();
    static {
        DTM_FILTER.elevationModel = ElevationData.MODEL_TERRAIN;
    }

    /**
     * Compute the corner points of a rectangle given a position, rotation,
     * and scale
     *
     * @param center Center point
     * @param length Length in meters
     * @param width Width in meters
     * @param heading Heading in degrees (true north)
     * @return [TL, TR, BR, BL]
     */
    public static GeoPoint[] computeCorners(GeoPoint center, double length,
            double width, double heading) {
        // Top left
        GeoPoint tl = DistanceCalculations.computeDestinationPoint(
                center, heading, length / 2);
        tl = DistanceCalculations.computeDestinationPoint(
                tl, heading - 90, width / 2);

        // Top right
        GeoPoint tr = DistanceCalculations.computeDestinationPoint(
                center, heading, length / 2);
        tr = DistanceCalculations.computeDestinationPoint(
                tr, heading + 90, width / 2);

        // Bottom right
        GeoPoint br = DistanceCalculations.computeDestinationPoint(
                center, heading + 180, length / 2);
        br = DistanceCalculations.computeDestinationPoint(
                br, heading + 90, width / 2);

        // Bottom left
        GeoPoint bl = DistanceCalculations.computeDestinationPoint(
                center, heading + 180, length / 2);
        bl = DistanceCalculations.computeDestinationPoint(
                bl, heading - 90, width / 2);

        return new GeoPoint[] {
                tl, tr, br, bl
        };
    }

    /**
     * Perform an elevation lookup for a point
     *
     * @param gpmd Point metadata
     * @return Point with altitude or no altitude if no source found
     */
    public static GeoPointMetaData getAltitude(GeoPointMetaData gpmd) {
        GeoPoint point = gpmd.get();
        double lat = point.getLatitude();
        double lng = point.getLongitude();
        ElevationManager.getElevation(lat, lng, DTM_FILTER, gpmd);
        return gpmd;
    }
}
