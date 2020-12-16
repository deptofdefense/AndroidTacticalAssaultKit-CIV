package com.atakmap.map.opengl;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.map.Interop;
import com.atakmap.map.elevation.ElevationChunk;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.ModelBuilder;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.math.Matrix;
import com.atakmap.util.ReadWriteLock;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public final class ElMgrTerrainRenderService extends TerrainRenderService {

    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            destruct(pointer);
        }
    };

    private static Interop<Mesh> Mesh_interop = Interop.findInterop(Mesh.class);

    private static final String TAG = "ElMgrTerrainRenderService";
    private final Cleaner cleaner;

    Pointer pointer;
    final ReadWriteLock rwlock = new ReadWriteLock();
    Object owner;

    Map<Pointer, TerrainTile> lockedTiles = new HashMap<>();

    ElMgrTerrainRenderService(long ctxptr) {
        this(create(ctxptr), null);
    }

    ElMgrTerrainRenderService(Pointer pointer, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    public final void dispose() {
        if(this.cleaner != null)
            this.cleaner.clean();
    }

    @Override
    public int lock(GLMapView view, Collection<TerrainTile> tiles) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return -1;
            LinkedList<Pointer> tilePtrs = new LinkedList<>();
            final int srcVersion = lock(
                    this.pointer.raw,
                    (view != null) ? view.pointer.raw : 0L,
                    tilePtrs);
            synchronized(lockedTiles) {
                this.elevationStats.reset();
                for(Pointer tilePtr : tilePtrs) {
                    TerrainTile tile = lockedTiles.get(tilePtr);
                    if(tile == null) {
                        // interop
                        tile = new TerrainTile(
                            new ElevationChunk.Data(),
                            TerrainTile_getNumIndices(tilePtr.raw),
                            TerrainTile_getSkirtIndexOffset(tilePtr.raw),
                            new Envelope(),
                            TerrainTile_hasData(tilePtr.raw),
                            TerrainTile_isHeightMap(tilePtr.raw),
                            TerrainTile_getNumPostsX(tilePtr.raw),
                            TerrainTile_getNumPostsY(tilePtr.raw),
                            TerrainTile_isInvertYAxis(tilePtr.raw)
                        );
                        tile.data.value = Mesh_interop.create(TerrainTile_getMesh(tilePtr.raw));
                        tile.data.srid = TerrainTile_getSrid(tilePtr.raw);
                        tile.data.localFrame = TerrainTile_getLocalFrame(tilePtr.raw);
                        tile.data.interpolated = true;

                        TerrainTile_getAAbbWgs84(tilePtr.raw, tile.aabbWgs84);

                        lockedTiles.put(tilePtr, tile);
                    }
                    tiles.add(tile);

                    this.elevationStats.observe(tile.aabbWgs84.minZ);
                    this.elevationStats.observe(tile.aabbWgs84.maxZ);
                }
            }

            return srcVersion;
        } finally {
            this.rwlock.releaseRead();
        }
    }

    // stub for chris to implement
    @Override
    public int getTerrainVersion() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return -1;
            return getTerrainVersion(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void unlock(Collection<TerrainTile> tiles) {
        this.rwlock.acquireRead();
        try {
            int idx = 0;
            Pointer[] unlocked = new Pointer[tiles.size()];
            synchronized(lockedTiles) {
                for(TerrainTile tile : tiles) {
                    Iterator<Map.Entry<Pointer, TerrainTile>> it;
                    it = lockedTiles.entrySet().iterator();
                    while(it.hasNext()) {
                        Map.Entry<Pointer, TerrainTile> entry = it.next();
                        if(entry.getValue() != tile)
                            continue;
                        unlocked[idx++] = entry.getKey();
                        it.remove();
                        break;
                    }
                }
            }

            // unlock
            if(this.pointer.raw == 0L)
                unlock(this.pointer.raw, unlocked, idx);
            // destruct
            for(int i = 0; i < idx; i++)
                TerrainTile_destruct(unlocked[i]);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getElevation(GeoPoint geo) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return ElevationManager.getElevation(geo.getLatitude(), geo.getLongitude(), null);
            return getElevation(this.pointer.raw, geo.getLatitude(), geo.getLongitude());
        } finally {
            this.rwlock.releaseRead();
        }
    }

    static native Pointer create(long ctxptr);
    static native void destruct(Pointer pointer);

    static native int getTerrainVersion(long pointer);
    static native int lock(long pointer, long viewPtr, Collection<Pointer> tilePtrs);
    static native void unlock(long pointer, Pointer[] tilePtrs, int count);
    static native double getElevation(long pointer, double lat, double lng);

    static native void TerrainTile_destruct(Pointer pointer);
    static native int TerrainTile_getSkirtIndexOffset(long pointer);
    static native int TerrainTile_getNumIndices(long pointer);
    static native Pointer TerrainTile_getMesh(long pointer);
    static native Matrix TerrainTile_getLocalFrame(long pointer);
    static native int TerrainTile_getSrid(long pointer);
    static native void TerrainTile_getAAbbWgs84(long pointer, Envelope aabb_wgs84);
    static native boolean TerrainTile_hasData(long pointer);
    static native boolean TerrainTile_isHeightMap(long pointer);
    static native int TerrainTile_getNumPostsX(long pointer);
    static native int TerrainTile_getNumPostsY(long pointer);
    static native boolean TerrainTile_isInvertYAxis(long pointer);
}
