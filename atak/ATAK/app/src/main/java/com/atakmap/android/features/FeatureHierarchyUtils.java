
package com.atakmap.android.features;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.Point;

public class FeatureHierarchyUtils {

    private final static ElevationManager.QueryParameters DTM_FILTER = new ElevationManager.QueryParameters();
    static {
        DTM_FILTER.elevationModel = ElevationData.MODEL_TERRAIN;
    }

    /**
     * Given a point compute the altitude based on the current terrain.
     * @param p the provided point
     * @return the GeoPoint with metadata.
     */
    static GeoPointMetaData getAltitude(Point p,
            Feature.AltitudeMode altitudeMode) {

        double elevation = ElevationManager.getElevation(
                p.getY(), p.getX(), DTM_FILTER);

        // add in the currently supplied altitude from the feature
        double alt = p.getZ();

        switch (altitudeMode) {
            case Relative:
                elevation = elevation + alt;
                break;
            case Absolute:
                elevation = alt;
                break;
        }
        GeoPoint gp = new GeoPoint(p.getY(), p.getX(), elevation);

        return GeoPointMetaData.wrap(gp);
    }
}
