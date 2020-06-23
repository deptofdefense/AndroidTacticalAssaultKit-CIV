
package com.atakmap.android.routes.elevation.model;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class RouteData extends SegmentData {

    private Integer[] _indices = new Integer[] {};
    private ControlPointData _controlPointData = new ControlPointData();
    private GeoPointMetaData[] _unexpandedSegment;
    private GeoPointMetaData _maxAlt;
    private GeoPointMetaData _minAlt;
    private double _maxSlope;
    private double _totalGain;
    private double _totalLoss;
    private boolean _interpolatedAlts;

    public RouteData() {
    }

    public Integer[] getIndices() {
        return _indices;
    }

    public void setIndices(Integer[] indices) {
        this._indices = indices;
    }

    public void setUnexpandedGeoPoints(GeoPointMetaData[] unexpanded) {
        this._unexpandedSegment = unexpanded;
    }

    public GeoPointMetaData[] getUnexpandedGeoPoints() {
        return this._unexpandedSegment;
    }

    public ControlPointData getControlPointData() {
        return _controlPointData;
    }

    public void setControlPointData(ControlPointData controlPointData) {
        this._controlPointData = controlPointData;
    }

    public void setMaxAlt(GeoPointMetaData maxAlt) {
        this._maxAlt = maxAlt;
    }

    public GeoPointMetaData getMaxAlt() {
        return _maxAlt;
    }

    public void setMinAlt(GeoPointMetaData minAlt) {
        this._minAlt = minAlt;
    }

    public GeoPointMetaData getMinAlt() {
        return _minAlt;
    }

    public void setMaxSlope(double maxSlope) {
        this._maxSlope = maxSlope;
    }

    public double getMaxSlope() {
        return _maxSlope;
    }

    public void setTotalGain(double totalGain) {
        this._totalGain = totalGain;
    }

    public double getTotalGain() {
        return _totalGain;
    }

    public void setTotalLoss(double totalLoss) {
        this._totalLoss = totalLoss;
    }

    public double getTotalLoss() {
        return _totalLoss;
    }

    public boolean getInterpolatedAltitudes() {
        return _interpolatedAlts;
    }

    public void setInterpolatedAltitudes(boolean interp) {
        _interpolatedAlts = interp;
    }

    @Override
    public RouteData copy() {
        SegmentData sdata = super.copy();
        RouteData data = sdata.toRouteData();
        data._indices = _indices;
        data._maxAlt = _maxAlt;
        data._minAlt = _minAlt;
        data._totalGain = _totalGain;
        data._totalLoss = _totalLoss;
        data._interpolatedAlts = _interpolatedAlts;
        if (_controlPointData != null) {
            data._controlPointData = _controlPointData.copy();
            data._controlPointData.setIndices(_controlPointData.getIndices()
                    .clone());
        }

        if (this._unexpandedSegment != null)
            data._unexpandedSegment = this._unexpandedSegment;
        return data;
    }

}
