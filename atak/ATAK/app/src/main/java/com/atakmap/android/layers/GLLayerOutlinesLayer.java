
package com.atakmap.android.layers;

import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchGeometry;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchGeometryRenderer;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchPoint;
import com.atakmap.map.layer.feature.opengl.GLFeatureLayer;
import com.atakmap.map.layer.feature.service.FeatureHitTestControl;
import com.atakmap.map.layer.opengl.GLAsynchronousLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.Tessellate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

class GLLayerOutlinesLayer extends
        GLAsynchronousLayer<Collection<Feature>>
        implements
        FeatureDataStore.OnDataStoreContentChangedListener,
        Layer.OnLayerVisibleChangedListener,
        FeatureHitTestControl {

    final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // OutlinesFeatureDatStore : FeatureLayer : Layer
            return 3;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (!(layer instanceof FeatureLayer))
                return null;
            final FeatureDataStore dataStore = ((FeatureLayer) layer)
                    .getDataStore();
            if (!(dataStore instanceof OutlinesFeatureDataStore))
                return null;
            return new GLLayerOutlinesLayer(surface, (FeatureLayer) layer);
        }
    };

    private final static Comparator<Feature> FID_COMPARATOR = new Comparator<Feature>() {
        @Override
        public int compare(Feature lhs, Feature rhs) {
            final long fid0 = lhs.getId();
            final long fid1 = rhs.getId();

            if (fid0 < fid1)
                return -1;
            else if (fid0 > fid1)
                return 1;
            else
                return 0;
        }
    };

    private final FeatureLayer subject;
    private final FeatureDataStore dataStore;

    private boolean visible = false;

    private Map<Long, RendererEntry> features;

    private Collection<GLMapRenderable> renderList;

    private GLBatchGeometryRenderer renderer;

    private GLLayerOutlinesLayer(MapRenderer surface, FeatureLayer subject) {
        super(surface, subject);

        this.subject = subject;
        this.dataStore = this.subject.getDataStore();

        this.features = new HashMap<>();

    }

    /**************************************************************************/
    // GL Layer

    @Override
    public void start() {
        super.start();

        this.subject.addOnLayerVisibleChangedListener(this);
        this.visible = this.subject.isVisible();

        this.renderContext.registerControl(this.subject, this);

        this.dataStore.addOnDataStoreContentChangedListener(this);
    }

    @Override
    public synchronized void stop() {
        super.stop();

        this.subject.removeOnLayerVisibleChangedListener(this);
        this.dataStore.removeOnDataStoreContentChangedListener(this);

        this.renderContext.unregisterControl(this.subject, this);
    }

    /**************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    protected String getBackgroundThreadName() {
        return "Outlines [" + this.subject.getName() + "] GL worker@"
                + Integer.toString(this.hashCode(), 16);
    }

    @Override
    protected void initImpl(GLMapView view) {
        super.initImpl(view);

        this.renderer = new GLBatchGeometryRenderer(view);
        this.renderList = Collections
                .<GLMapRenderable> singleton(this.renderer);
    }

    @Override
    protected void releaseImpl() {
        if (this.renderer != null) {
            this.renderer.release();
            this.renderer = null;
        }
        for (RendererEntry feature : this.features.values())
            for (GLBatchGeometry g : feature.renderer)
                g.release();
        this.features.clear();

        this.renderList = null;

        super.releaseImpl();
    }

    @Override
    protected Collection<? extends GLMapRenderable> getRenderList() {
        if (!this.visible || this.renderList == null)
            return Collections.emptySet();
        return this.renderList;
    }

    @Override
    protected void resetPendingData(Collection<Feature> pendingData) {
        pendingData.clear();
    }

    @Override
    protected void releasePendingData(Collection<Feature> pendingData) {
        pendingData.clear();
    }

    @Override
    protected Collection<Feature> createPendingData() {
        return new LinkedList<>();
    }

    @Override
    protected boolean updateRenderableReleaseLists(
            Collection<Feature> pendingData) {
        Map<Long, RendererEntry> swap = new HashMap<>();

        Iterator<Feature> iter = pendingData.iterator();
        Feature feature;
        RendererEntry entry;
        while (iter.hasNext()) {
            feature = iter.next();
            entry = this.features.remove(feature.getId());
            if (entry == null)
                entry = new RendererEntry();

            if (entry.version != feature.getVersion()) {
                entry.renderer.clear();
                entry.renderer.ensureCapacity(recurseGeometry(null, 0L,
                        feature.getGeometry(), null, null));
                recurseGeometry(this.renderContext, feature.getId(),
                        feature.getGeometry(), feature.getStyle(),
                        entry.renderer);
                entry.version = feature.getVersion();
            }
            swap.put(feature.getId(), entry);
            iter.remove();
        }

        if (this.features.size() > 0) {
            final Collection<RendererEntry> releaseList = this.features
                    .values();
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    for (RendererEntry f : releaseList)
                        for (GLBatchGeometry g : f.renderer)
                            g.release();
                }
            });
        }

        this.features = swap;

        LinkedList<GLBatchGeometry> geoms = new LinkedList<>();
        for (RendererEntry e : this.features.values())
            geoms.addAll(e.renderer);

        // set the render content on the batch. both this method and 'draw' are
        // invoked while holding 'this'
        this.renderer.setBatch(geoms);

        return true;
    }

    @Override
    protected void query(ViewState state, Collection<Feature> retval) {
        if (state.crossesIDL) {
            double east = Math.min(state.westBound, state.eastBound) + 360;
            double west = Math.max(state.westBound, state.eastBound);
            this.queryImpl(state.northBound, west,
                    state.southBound, east,
                    state.drawMapResolution,
                    retval);
        } else {
            this.queryImpl(state.northBound,
                    state.westBound,
                    state.southBound,
                    state.eastBound,
                    state.drawMapResolution,
                    retval);
        }
    }

    private void queryImpl(double northBound,
            double westBound,
            double southBound,
            double eastBound,
            double drawMapResolution,
            Collection<Feature> retval) {

        FeatureCursor result = null;
        try {
            FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
            params.spatialFilter = new FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter(
                    new GeoPoint(northBound, westBound),
                    new GeoPoint(southBound, eastBound));
            params.maxResolution = drawMapResolution;
            params.visibleOnly = true;

            if (this.checkQueryThreadAbort())
                return;

            //long s = android.os.SystemClock.elapsedRealtime();
            result = this.dataStore.queryFeatures(params);
            while (result.moveToNext()) {
                if (this.checkQueryThreadAbort())
                    break;
                retval.add(result.get());
            }
            //long e = android.os.SystemClock.elapsedRealtime();
            //Log.d(TAG, retval.size() + " results in " + (e-s) + "ms");
        } finally {
            if (result != null)
                result.close();
        }
    }

    /**************************************************************************/
    // Feature Data Store On Data Store Content Changed Listener

    @Override
    public void onDataStoreContentChanged(FeatureDataStore dataStore) {
        if (GLMapSurface.isGLThread()) {
            invalidate();
        } else {
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLLayerOutlinesLayer.this.invalidateNoSync();
                }
            });
        }
    }

    @Override
    public void onLayerVisibleChanged(Layer layer) {
        final boolean visible = layer.isVisible();
        if (GLMapSurface.isGLThread())
            this.visible = visible;
        else
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLLayerOutlinesLayer.this.visible = visible;
                }
            });
    }

    /**************************************************************************/
    // HitTestService

    @Override
    public synchronized void hitTest(Collection<Long> fids,
            float screenX, float screenY,
            GeoPoint point, double resolution,
            float radius, int limit) {

        Feature f;
        for (Map.Entry<Long, RendererEntry> entry : this.features.entrySet()) {
            f = this.dataStore.getFeature(entry.getKey());
            if (f != null) {
                if (GLFeatureLayer.hitTest(f.getGeometry(), point, radius
                        * resolution)) {
                    fids.add(f.getId());
                    if (fids.size() == limit)
                        break;
                }
            }
        }
    }

    /**************************************************************************/

    private static int recurseGeometry(MapRenderer surface, long fid,
            Geometry geom, Style style, Collection<GLBatchGeometry> renderer) {
        if (geom instanceof Point) {
            if (renderer != null) {
                GLBatchPoint r = new GLBatchPoint(surface);
                r.init((fid << 20L) | (renderer.size() & 0xFFFFF), null);
                r.setGeometry((Point) geom);
                r.setStyle(style);
                renderer.add(r);
            }
            return 1;
        } else if (geom instanceof LineString) {
            if (renderer != null) {
                GLBatchLineString r = new GLBatchLineString(surface);
                r.init((fid << 20L) | (renderer.size() & 0xFFFFF), null);
                r.setGeometry((LineString) geom);
                r.setStyle(style);
                r.setTessellationEnabled(true);
                // we need to use XYZ tessellation mode here
                r.setTessellationMode(Tessellate.Mode.XYZ);
                renderer.add(r);
            }
            return 1;
        } else if (geom instanceof Polygon) {
            if (renderer != null) {
                recurseGeometry(surface, fid,
                        ((Polygon) geom).getExteriorRing(), style, renderer);
                for (LineString ring : ((Polygon) geom).getInteriorRings())
                    recurseGeometry(surface, fid, ring, style, renderer);
            }
            return 1 + ((Polygon) geom).getInteriorRings().size();
        } else if (geom instanceof GeometryCollection) {
            int retval = 0;
            for (Geometry child : ((GeometryCollection) geom).getGeometries())
                retval += recurseGeometry(surface, fid, child, style, renderer);
            return retval;
        } else {
            throw new IllegalStateException();
        }
    }

    /**************************************************************************/

    private static class RendererEntry {
        public long version;
        public final ArrayList<GLBatchGeometry> renderer;

        public RendererEntry() {
            this.version = -1L;
            this.renderer = new ArrayList<>(1);
        }
    }

} // GLLayersOutlineLayer
