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

    /**
     * Obtains the terrain tiles to be rendered for the scene. The tiles are
     * <I>locked</I>, and the mesh data is not eligible for deletion until
     * they are <I>unlocked</I> via {@link #unlock(Collection)}
     * @param view  The current scene. If <code>null</code> is specified, the
     *              the tiles returned from the last call to
     *              {@link #lock(GLMapView, Collection)} will be returned.
     * @param tiles The terrain tiles
     * @return  The terrain version associated with the returned tiles
     */
    public abstract int lock(GLMapView view, Collection<TerrainTile> tiles);

    /**
     * <I>Unlocks</I> tiles that were previously returned from a call to
     * {@link #lock(GLMapView, Collection)}. After a
     * {@link GLMapView.TerrainTile} instance has no more locks it becomes
     * eligible for destruction.
     *
     * @param tiles The tiles to be unlocked
     */
    public abstract void unlock(Collection<TerrainTile> tiles);
    public abstract double getElevation(GeoPoint geo);
    public abstract void dispose();
}
