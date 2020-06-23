
package com.atakmap.android.toolbars;

import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.routes.Route;

import java.util.UUID;

class RouteRangeAndBearingWrapper extends Route {
    private final RangeAndBearingMapItem filter;
    private final Marker start, end;
    private boolean updatingPoints;

    public RouteRangeAndBearingWrapper(MapView mapView,
            RangeAndBearingMapItem filter,
            MapGroup mapGroup) {
        super(mapView, "Range Bearing", 0, "CP", UUID.randomUUID().toString());

        this.setMetaBoolean("__ignoreRefresh", true);

        this.filter = filter;
        this.updatingPoints = false;

        this.start = createWayPoint(this.filter.getPoint1(),
                UUID.randomUUID().toString());
        this.start.setMetaString("callsign", "Start");
        this.setMarker(0, this.start);

        this.end = createWayPoint(this.filter.getPoint2(),
                UUID.randomUUID().toString());
        this.end.setMetaString("callsign", "End");
        this.setMarker(1, this.end);

        this.filter.addOnPointsChangedListener(_listener);

        this.updateImpl();
    }

    private void updateImpl() {
        this.updatingPoints = true;
        this.clearPoints();

        this.setPoints(this.filter.getMetaDataPoints());
        this.start.setPoint(this.filter.getPoint1());
        this.end.setPoint(this.filter.getPoint2());

        this.updatingPoints = false;

        this.onRoutePointsChanged();
    }

    @Override
    protected void onRoutePointsChanged() {
        if (!this.updatingPoints)
            super.onRoutePointsChanged();
    }

    @Override
    public void dispose() {
        this.onGroupChanged(false, new DefaultMapGroup());
        this.filter.removeOnPointsChangedListener(_listener);
        this.start.dispose();
        this.end.dispose();
        super.dispose();
    }

    private final Shape.OnPointsChangedListener _listener = new Shape.OnPointsChangedListener() {
        @Override
        public void onPointsChanged(Shape s) {
            updateImpl();
        }
    };
}
