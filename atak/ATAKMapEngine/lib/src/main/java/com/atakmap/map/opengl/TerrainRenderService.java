package com.atakmap.map.opengl;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.math.Statistics;

import java.util.Collection;

public abstract class TerrainRenderService {
    protected final static ModelInfo WGS84 = new ModelInfo();
    static {
        WGS84.srid = 4326;
    }

    protected final Statistics elevationStats = new Statistics();

    public TerrainRenderService() {
    }

    public abstract int getTerrainVersion();
    public abstract int lock(GLMapView view, Collection<GLMapView.TerrainTile> tiles);
    public abstract void unlock(Collection<GLMapView.TerrainTile> tiles);
    public abstract double getElevation(GeoPoint geo);
    public abstract void dispose();
}
