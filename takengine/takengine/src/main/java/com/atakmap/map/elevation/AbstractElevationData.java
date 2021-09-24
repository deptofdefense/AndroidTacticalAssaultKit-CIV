package com.atakmap.map.elevation;

import java.util.Iterator;

import com.atakmap.coremap.maps.coords.GeoPoint;

public abstract class AbstractElevationData implements ElevationData {

    protected final int model;
    protected final String type;
    protected final double resolution;
    
    protected AbstractElevationData(int model, String type, double resolution) {
        this.model = model;
        this.type = type;
        this.resolution = resolution;
    }
    
    @Override
    public final int getElevationModel() {
        return this.model;
    }

    @Override
    public final String getType() {
        return this.type;
    }

    @Override
    public double getResolution() {
        return this.resolution;
    }

    @Override
    public abstract double getElevation(double latitude, double longitude);

    @Override
    public void getElevation(Iterator<GeoPoint> points, double[] elevations, Hints ignored) {
        int index = 0;
        GeoPoint point;
        while(points.hasNext()) {
            point = points.next();
            elevations[index++] = this.getElevation(point.getLatitude(), point.getLongitude());
        }
    }
}
