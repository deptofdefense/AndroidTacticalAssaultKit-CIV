package com.atakmap.map.layer.model.contextcapture;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** @deprecated PROTOTYPE CODE; SUBJECT TO REMOVAL AT ANY TIME; DO NOT CREATE DIRECT DEPENDENCIES */
@Deprecated
@DeprecatedApi(since = "4.1")
interface GLTileNode extends GLMapRenderable2 {
    public enum RenderVisibility {
        None,
        Prefetch,
        Draw,
    }

    public static class LoadContext {
        private static AtomicInteger idGenerator = new AtomicInteger(0);

        /** content centroid */
        public GeoPoint centroid;
        /** content bounding sphere radius, in meters */
        public double boundingSphereRadius;
        /** content nominal resolution, in meters */
        public double gsd;

        public Object opaque;

        int id = idGenerator.getAndIncrement();
    }

    public void asyncLoad(LoadContext ctx, AtomicBoolean cancelToken);
    public boolean isLoaded(GLMapView view);
    public LoadContext prepareLoadContext(GLMapView view);
    public RenderVisibility isRenderable(GLMapView view);
    public void unloadLODs();
    public boolean hasLODs();
}
