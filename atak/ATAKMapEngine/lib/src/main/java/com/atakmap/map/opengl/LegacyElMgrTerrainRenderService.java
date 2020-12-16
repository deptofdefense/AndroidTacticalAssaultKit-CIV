package com.atakmap.map.opengl;


import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Collection;

/** @deprecated Use {@link ElMgrTerrainRenderService} */
@Deprecated
@DeprecatedApi(since="4.2", forRemoval = true, removeAt = "4.5")
public class LegacyElMgrTerrainRenderService extends TerrainRenderService {
    private final static int MAX_GRID_NODE_LEVEL = 18;

    public LegacyElMgrTerrainRenderService() {}

    @Override
    public void dispose() {}

    // legacy implementation
    @Override
    public int getTerrainVersion() { return 0; }

    @Override
    public int lock(GLMapView view, Collection<TerrainTile> tiles) { return getTerrainVersion(); }

    @Override
    public void unlock(Collection<TerrainTile> tiles) {}

    @Override
    public double getElevation(GeoPoint geo) {
        return Double.NaN;
    }
}
