
package com.atakmap.map.layer.raster.tilematrix;

import com.atakmap.map.contentservices.CacheRequest;
import com.atakmap.map.contentservices.CacheRequestListener;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.util.Collection;

public class MockTileClient extends MockTileMatrix implements TileClient {
    boolean authFailed;

    public MockTileClient(String name, int srid, ZoomLevel[] levels,
            double originX, double originY, Envelope bounds) {
        super(name, srid, levels, originX, originY, bounds);

        authFailed = false;
    }

    @Override
    public void clearAuthFailed() {
        authFailed = false;
    }

    @Override
    public void checkConnectivity() {
        // no-op
    }

    @Override
    public void cache(CacheRequest request, CacheRequestListener listener) {
        // no-op
    }

    @Override
    public int estimateTileCount(CacheRequest request) {
        return 0;
    }

    @Override
    public <T> T getControl(Class<T> controlClazz) {
        // no-op
        return null;
    }

    @Override
    public void getControls(Collection<Object> controls) {
        // no-op
    }
}
