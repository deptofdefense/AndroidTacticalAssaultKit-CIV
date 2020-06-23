
package com.atakmap.android.routes.elevation.model;

import com.atakmap.coremap.maps.coords.GeoPoint;

public class ControlPointData {
    private String[] _names = new String[] {};
    private double[] _distances = new double[] {};
    private GeoPoint[] _geoPoints = new GeoPoint[] {};
    private int[] _indices = new int[] {};

    public ControlPointData() {
        super();
    }

    public String[] getNames() {
        return _names;
    }

    public void setNames(String[] names) {
        this._names = names;
    }

    public double[] getDistances() {
        return _distances;
    }

    public void setDistances(double[] distances) {
        this._distances = distances;
    }

    public GeoPoint[] getGeoPoints() {
        return _geoPoints;
    }

    public void setGeoPoints(GeoPoint[] geoPoints) {
        this._geoPoints = geoPoints;
    }

    public int[] getIndices() {
        return this._indices;
    }

    public void setIndices(int[] indices) {
        this._indices = indices;
    }

    public ControlPointData copy() {
        ControlPointData data = new ControlPointData();
        data._names = _names.clone();
        data._distances = _distances.clone();
        data._geoPoints = _geoPoints.clone();
        data._indices = _indices.clone();
        return data;
    }
}
