
package com.atakmap.android.routes;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;

import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;

import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * If a route extends this class, the RouteElevationPresenter will 
 * show the points in the pointArr as points "floating" above the graph
 */
public abstract class FloatingPointRoute extends Route {
    protected boolean updatingPoints = false;
    protected List<GeoPoint> pointArr;

    protected double referencePointElev = 0;

    public FloatingPointRoute() {
        super(MapView.getMapView(), "Range Bearing", 0, "", UUID.randomUUID()
                .toString());
        pointArr = new ArrayList<>();
    }

    /**
     * Create a route with points to show above the graph
     */
    protected abstract void createRoute();

    /**
     * find the marker in the subgroup with the given callsign, then set it as a marker and add it
     * to the point list.
     */
    protected void addMarkerToRoute(MapGroup subgroup, String callsign) {
        if (subgroup == null)
            return;
        Marker pointOnPath = (Marker) subgroup.findItem("callsign", callsign);
        if (pointOnPath == null)
            return;

        if (pointOnPath.getPoint()
                .getAltitudeReference() == AltitudeReference.AGL) {
            GeoPoint gp = pointOnPath.getPoint();
            double markerAltMSL = gp.getAltitude()
                    + referencePointElev;
            gp = new GeoPoint(gp.getLatitude(), gp.getLongitude(),
                    EGM96.getHAE(gp.getLatitude(),
                            gp.getLongitude(), markerAltMSL));
            pointOnPath.setPoint(gp);
        }

        pointArr.add(pointOnPath.getPoint());
    }

    @Override
    protected void onRoutePointsChanged() {
        if (!this.updatingPoints)
            super.onRoutePointsChanged();
    }

    public List<GeoPoint> getFloatingGeoPoints() {
        return pointArr;
    }
}
