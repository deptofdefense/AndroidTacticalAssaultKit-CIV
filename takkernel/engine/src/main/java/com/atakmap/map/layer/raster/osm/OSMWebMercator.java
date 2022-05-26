package com.atakmap.map.layer.raster.osm;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.WebMercatorProjection;
import com.atakmap.math.PointD;

public final class OSMWebMercator implements Projection {

    public final static Projection INSTANCE = new OSMWebMercator();

    private final static double MIN_LAT = OSMUtils.mapnikTileLat(0, 1);
    private final static double MIN_LNG = OSMUtils.mapnikTileLng(0, 0);
    private final static double MAX_LAT = OSMUtils.mapnikTileLat(0, 0);
    private final static double MAX_LNG = OSMUtils.mapnikTileLng(0, 1);
    
    private OSMWebMercator() {}

    @Override
    public double getMinLatitude() { return MIN_LAT; }
    @Override
    public double getMinLongitude() { return MIN_LNG; }
    @Override
    public double getMaxLatitude() { return MAX_LAT; }
    @Override
    public double getMaxLongitude() { return MAX_LNG; }

    @Override
    public PointD forward(GeoPoint g, PointD p) {
        return WebMercatorProjection.INSTANCE.forward(g, p);
    }

    @Override
    public GeoPoint inverse(PointD p, GeoPoint g) {
        return WebMercatorProjection.INSTANCE.inverse(p, g);
    }

    @Override
    public int getSpatialReferenceID() {
        return WebMercatorProjection.INSTANCE.getSpatialReferenceID();
    }

    @Override
    public boolean is3D() {
        return WebMercatorProjection.INSTANCE.is3D();
    }
}