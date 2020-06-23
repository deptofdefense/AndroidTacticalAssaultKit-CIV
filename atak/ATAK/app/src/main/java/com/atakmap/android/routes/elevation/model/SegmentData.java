
package com.atakmap.android.routes.elevation.model;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class SegmentData {
    private GeoPointMetaData[] _geoPoints = new GeoPointMetaData[] {};
    private double[] _distances = new double[] {};
    private double _totalDistance;

    public SegmentData() {
    }

    public GeoPointMetaData[] getGeoPoints() {
        return _geoPoints;
    }

    public void setGeoPoints(GeoPointMetaData[] geoPoints) {
        this._geoPoints = geoPoints;
    }

    public double[] getDistances() {
        return _distances;
    }

    public void setDistances(double[] distances) {
        this._distances = distances;
    }

    public double getTotalDistance() {
        return _totalDistance;
    }

    public void setTotalDistance(double totalDistance) {
        this._totalDistance = totalDistance;
    }

    public SegmentData copy() {
        SegmentData data = new SegmentData();
        data._geoPoints = _geoPoints.clone();
        data._distances = _distances.clone();
        data._totalDistance = _totalDistance;
        return data;
    }

    public RouteData toRouteData() {
        RouteData data = new RouteData();
        data.setGeoPoints(this._geoPoints);
        data.setDistances(this._distances);
        data.setTotalDistance(this._totalDistance);
        return data;
    }
}
