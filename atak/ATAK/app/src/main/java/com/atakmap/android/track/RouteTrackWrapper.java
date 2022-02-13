
package com.atakmap.android.track;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.routes.Route;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;

import java.util.UUID;

public class RouteTrackWrapper extends Route {

    private final TrackDetails track;
    private boolean updatingPoints;

    public RouteTrackWrapper(MapView mapView, TrackDetails track) {
        super(mapView, track.getTitle(), track.getColor(), "CP", track
                .getTrackUID(), false);

        this.setStyle(TrackDetails.getBasicStyle(track.getPolyline()));
        this.setMetaBoolean("__ignoreRefresh", true);

        this.track = track;
        this.updatingPoints = false;

        //TODO should this listener be removed at some point?
        this.track.getPolyline().addOnPointsChangedListener(
                new Shape.OnPointsChangedListener() {
                    @Override
                    public void onPointsChanged(Shape s) {
                        updateImpl();
                    }
                });

        this.updateImpl();
    }

    private void updateImpl() {
        this.updatingPoints = true;
        this.clearPoints();

        GeoPointMetaData[] points = this.track.getPolyline()
                .getMetaDataPoints();
        this.setPoints(points);

        Marker cp0 = createWayPoint(points[0], UUID.randomUUID().toString());
        cp0.setMetaString("callsign", "Start");
        this.setMarker(0, cp0);

        Marker cp1 = createWayPoint(points[points.length - 1], UUID
                .randomUUID().toString());
        cp1.setMetaString("callsign", "End");
        this.setMarker(points.length - 1, cp1);

        setAltitudeMode(AltitudeMode.Absolute);

        this.updatingPoints = false;

        this.onRoutePointsChanged();
    }

    @Override
    protected void onRoutePointsChanged() {
        if (!this.updatingPoints)
            super.onRoutePointsChanged();
    }
}
