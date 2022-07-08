
package com.atakmap.android.model.opengl;

import android.opengl.GLES30;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.control.ColorControl;
import com.atakmap.map.layer.control.Controls;
import com.atakmap.map.layer.control.RendererRefreshControl;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDefinition2;
import com.atakmap.map.layer.feature.FeatureLayer3;
import com.atakmap.map.layer.feature.Utils;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.service.FeatureHitTestControl;
import com.atakmap.map.layer.model.ModelHitTestControl;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.SceneObjectControl;
import com.atakmap.map.layer.model.opengl.GLSceneFactory;
import com.atakmap.map.layer.model.opengl.MaterialManager;
import com.atakmap.map.layer.opengl.GLAsynchronousLayer2;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.spatial.GeometryTransformer;
import com.atakmap.util.Collections2;
import gov.tak.api.util.Disposable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public final class GLModelLayer
        extends GLAsynchronousLayer2<Collection<GLMapRenderable2>>
        implements FeatureDataStore2.OnDataStoreContentChangedListener,
        FeatureHitTestControl,
        RendererRefreshControl {

    private final static String TAG = "GLModelLayer";

    private final FeatureDataStore2 dataStore;
    private Collection<GLMapRenderable2> drawList;
    private final Map<Long, SceneRenderer> cache;
    private final Map<Long, MaterialManager> materialManagers;
    private final File resourceDirectory;

    private final ModelHitTestControl modelHitTestControl = new ModelHitTestControl() {
        @Override
        public boolean hitTest(float screenX, float screenY,
                GeoPoint result) {
            Collection<ModelHitTestControl> r;
            synchronized (GLModelLayer.this) {
                r = new ArrayList<>(drawList.size());
                for (GLMapRenderable2 mr : drawList) {
                    if (mr instanceof ModelHitTestControl)
                        r.add((ModelHitTestControl) mr);
                    else if (mr instanceof Controls && ((Controls) mr)
                            .getControl(ModelHitTestControl.class) != null)
                        r.add(((Controls) mr)
                                .getControl(ModelHitTestControl.class));
                }
            }

            for (ModelHitTestControl mr : r)
                if (mr.hitTest(screenX, screenY, result))
                    return true;
            return false;
        }
    };

    public GLModelLayer(MapRenderer surface, FeatureLayer3 subject) {
        super(surface, subject);
        this.dataStore = subject.getDataStore();

        this.drawList = Collections.emptySet();
        this.cache = new HashMap<>();
        this.materialManagers = new HashMap<>();

        this.resourceDirectory = FileSystemUtils
                .getItem("Databases/models.db/resources");
    }

    @Override
    public void start() {
        super.start();
        this.dataStore.addOnDataStoreContentChangedListener(this);
        this.renderContext.registerControl((FeatureLayer3) this.subject, this);
        this.renderContext.registerControl((FeatureLayer3) this.subject,
                this.modelHitTestControl);
    }

    @Override
    public void stop() {
        this.renderContext.unregisterControl((FeatureLayer3) this.subject,
                this);
        this.renderContext.unregisterControl((FeatureLayer3) this.subject,
                this.modelHitTestControl);
        this.dataStore.removeOnDataStoreContentChangedListener(this);
        super.stop();
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        final boolean enabled = GLES30.glIsEnabled(GLES30.GL_DEPTH_TEST);
        final boolean[] mask = new boolean[1];
        GLES30.glGetBooleanv(GLES30.GL_DEPTH_WRITEMASK, mask, 0);
        final int[] func = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_DEPTH_FUNC, func, 0);
        if (!enabled)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        if (!mask[0])
            GLES30.glDepthMask(true);
        if (func[0] != GLES30.GL_LEQUAL)
            GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        super.draw(view, renderPass);
        if (!enabled)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        if (!mask[0])
            GLES30.glDepthMask(mask[0]);
        if (func[0] != GLES30.GL_LEQUAL)
            GLES30.glDepthFunc(func[0]);
    }

    @Override
    protected Collection<? extends GLMapRenderable2> getRenderList() {
        return this.drawList;
    }

    @Override
    protected void resetPendingData(Collection<GLMapRenderable2> pendingData) {
        pendingData.clear();
    }

    @Override
    protected void releasePendingData(
            Collection<GLMapRenderable2> pendingData) {
        pendingData.clear();
    }

    @Override
    protected Collection<GLMapRenderable2> createPendingData() {
        return new LinkedList<>();
    }

    @Override
    protected boolean updateRenderList(ViewState state,
            Collection<GLMapRenderable2> pendingData) {
        final Collection<GLMapRenderable2> toRelease = Collections2
                .newIdentityHashSet(this.drawList);
        this.drawList = new ArrayList<>(pendingData);
        for (GLMapRenderable2 r : drawList)
            toRelease.remove(r);

        if (!toRelease.isEmpty())
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    for (GLMapRenderable2 r : toRelease)
                        r.release();
                }
            });
        return true;
    }

    @Override
    protected void query(ViewState state, Collection<GLMapRenderable2> result) {

        ViewState scratch = this.newViewStateInstance();
        scratch.copy(state);
        try {
            if (state.crossesIDL) {
                Set<GLMapRenderable2> interim = Collections.newSetFromMap(
                        new IdentityHashMap<GLMapRenderable2, Boolean>());

                // west of IDL
                state.eastBound = 180d;
                queryImpl(state, interim);

                // reset
                state.copy(scratch);

                // east of IDL
                state.westBound = -180d;
                queryImpl(state, interim);

                result.addAll(interim);
            } else {
                queryImpl(state, result);
            }
        } finally {
            state.copy(scratch);
        }
    }

    private void queryImpl(
            ViewState state,
            Collection<GLMapRenderable2> retval) {
        // XXX - query and create models
        FeatureCursor result = null;
        try {
            FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
            params.spatialFilter = GeometryFactory.fromEnvelope(
                    new Envelope(state.westBound, state.southBound, 0d,
                            state.eastBound, state.northBound, 0d));
            params.featureSetFilter = new FeatureDataStore2.FeatureSetQueryParameters();
            params.featureSetFilter.maxResolution = state.drawMapResolution;
            params.visibleOnly = true;

            if (this.checkQueryThreadAbort())
                return;

            //long s = System.currentTimeMillis();
            result = this.dataStore.queryFeatures(params);
            while (result.moveToNext()) {
                if (this.checkQueryThreadAbort())
                    break;
                SceneRenderer entry;
                synchronized (cache) {
                    entry = cache.get(result.getId());
                    if (entry != null) {
                        if (entry.version != result.getVersion()) {
                            if (entry.value instanceof GLModelRenderer2)
                                ((GLModelRenderer2) entry.value).refresh();
                        }
                    } else {
                        entry = new SceneRenderer(result.get());
                        cache.put(result.getId(), entry);
                    }
                }

                retval.add(entry.value);
            }
            //long e = System.currentTimeMillis();
            //System.out.println(retval.size() + " results in " + (e-s) + "ms");
        } catch (DataStoreException e) {
            Log.w(TAG, "[" + getSubject().getName() + "] query failed.", e);
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SCENES;
    }

    static ModelInfo getModelInfo(AttributeSet attrs) {
        ModelInfo retval = new ModelInfo();
        retval.uri = attrs.getStringAttribute("uri");
        if (attrs.containsAttribute("type"))
            retval.type = attrs.getStringAttribute("type");
        retval.srid = attrs.getIntAttribute("srid");
        retval.altitudeMode = ModelInfo.AltitudeMode
                .valueOf(attrs.getStringAttribute("altitudeMode"));
        if (attrs.containsAttribute("localFrame")) {
            double[] mx = attrs.getDoubleArrayAttribute("localFrame");
            retval.localFrame = new Matrix(mx[0], mx[1], mx[2], mx[3], mx[4],
                    mx[5], mx[6], mx[7], mx[8], mx[9], mx[10], mx[11], mx[12],
                    mx[13], mx[14], mx[15]);
        }
        if (attrs.containsAttribute("location")) {
            AttributeSet loc = attrs.getAttributeSetAttribute("location");
            if (loc != null && loc.containsAttribute("latitude")
                    && loc.containsAttribute("longitude")) {
                retval.location = new GeoPoint(
                        loc.getDoubleAttribute("latitude"),
                        loc.getDoubleAttribute("longitude"));
            }
        }
        if (attrs.containsAttribute("resourceMap")) {
            AttributeSet resourceMap = attrs
                    .getAttributeSetAttribute("resourceMap");
            if (resourceMap != null) {
                for (String key : resourceMap.getAttributeNames()) {
                    final String value = resourceMap.getStringAttribute(key);
                    if (retval.resourceMap == null)
                        retval.resourceMap = new HashMap<>();
                    retval.resourceMap.put(key, value);
                }
            }
        }
        return retval;
    }

    public static ModelInfo getModelInfo(final Feature f) {
        AttributeSet attribs = f.getAttributes();
        if (attribs == null)
            return null;
        if (!attribs.containsAttribute("TAK.ModelInfo"))
            return null;
        attribs = attribs.getAttributeSetAttribute("TAK.ModelInfo");

        if (attribs == null)
            return null;

        return getModelInfo(attribs);
    }

    @Override
    public void onDataStoreContentChanged(FeatureDataStore2 dataStore) {
        invalidate();
    }

    @Override
    public void onFeatureInserted(FeatureDataStore2 dataStore, long fid,
            FeatureDefinition2 def, long version) {
        invalidate();
    }

    @Override
    public void onFeatureUpdated(FeatureDataStore2 dataStore, long fid,
            int modificationMask, String name, Geometry geom, Style style,
            AttributeSet attribs, int attribsUpdateType) {
        invalidate();
    }

    @Override
    public void onFeatureDeleted(FeatureDataStore2 dataStore, long fid) {
        invalidate();
    }

    @Override
    public void onFeatureVisibilityChanged(FeatureDataStore2 dataStore,
            long fid, boolean visible) {
        invalidate();
    }

    @Override
    public void requestRefresh() {
        synchronized (cache) {
            for (SceneRenderer r : cache.values())
                r.rendererRefreshRequested();
        }
        invalidateNoSync();
    }

    @Override
    public synchronized void hitTest(Collection<Long> fids, float screenX,
            float screenY, GeoPoint point, double resolution, float radius,
            int limit) {
        final PointD loc = new PointD(point.getLongitude(),
                point.getLatitude());
        final double rlat = Math.toRadians(loc.y);
        final double metersDegLat = 111132.92 - 559.82 * Math.cos(2 * rlat)
                + 1.175 * Math.cos(4 * rlat);
        final double metersDegLng = 111412.84 * Math.cos(rlat)
                - 93.5 * Math.cos(3 * rlat);

        final double thresholdMeters = resolution * radius;
        final double ra = thresholdMeters / metersDegLat;
        final double ro = thresholdMeters / metersDegLng;

        final Envelope hitBox = new Envelope(loc.x - ro, loc.y - ra, Double.NaN,
                loc.x + ro, loc.y + ra, Double.NaN);

        for (GLMapRenderable2 r : this.drawList) {
            if (!(r instanceof GLModelRenderer2))
                continue;
            GLModelRenderer2 mr = (GLModelRenderer2) r;
            final Envelope mbb = mr.featureBounds;
            if (mbb == null)
                continue;

            if (!Rectangle.intersects(mbb.minX, mbb.minY, mbb.maxX, mbb.maxY,
                    hitBox.minX, hitBox.minY, hitBox.maxX, hitBox.maxY))
                continue;

            fids.add(mr.feature.getId());
        }
    }

    public static File getCacheDir(Feature feature) {
        return FileSystemUtils
                .getItem("Databases/models.db/resources/"
                        + feature.getId());
    }

    class SceneRenderer
            implements SceneObjectControl.OnBoundsChangedListener, Disposable {
        GLMapRenderable2 value;
        ModelInfo info;
        ColorControl color;
        ModelHitTestControl hittest;
        SceneObjectControl ctrl;
        File cacheDir;
        long fid;
        long version;

        public SceneRenderer(Feature feature) {
            this.fid = feature.getId();
            final long fsid = feature.getFeatureSetId();

            info = getModelInfo(feature);
            cacheDir = getCacheDir(feature);
            value = GLSceneFactory.create(renderContext, info,
                    cacheDir.getAbsolutePath());
            if (value == null) {
                MaterialManager materialManager = materialManagers
                        .get(fsid);
                if (materialManager == null) {
                    File featuresetResources = new File(resourceDirectory,
                            "featureset");
                    materialManager = new MaterialManager(
                            renderContext,
                            new GLModelRenderer2.TextureLoader(
                                    renderContext,
                                    new File(featuresetResources,
                                            String.valueOf(fsid)),
                                    4096));
                    materialManagers.put(feature.getFeatureSetId(),
                            materialManager);
                }

                value = new GLModelRenderer2(
                        renderContext, feature, dataStore, materialManager);
            }

            version = feature.getVersion();

            initFromRenderer();
        }

        void rendererRefreshRequested() {
            if (this.value instanceof GLModelRenderer2) {
                final GLMapRenderable2 newRenderer = GLSceneFactory.create(
                        renderContext, info,
                        cacheDir.getAbsolutePath());
                if (newRenderer == null)
                    return;

                renderContext.queueEvent(new Runnable() {
                    public void run() {
                        final GLMapRenderable2 oldRenderer = value;

                        // swap the renderers
                        value = newRenderer;
                        // rewire the controls
                        initFromRenderer();
                        // release the old renderer
                        oldRenderer.release();

                        invalidateNoSync();
                    }
                });
            }
        }

        void initFromRenderer() {
            if (ctrl != null)
                ctrl.removeOnSceneBoundsChangedListener(this);

            if (value instanceof Controls) {
                color = ((Controls) value).getControl(ColorControl.class);
                hittest = ((Controls) value)
                        .getControl(ModelHitTestControl.class);
                ctrl = ((Controls) value).getControl(SceneObjectControl.class);
            } else if (value instanceof GLModelRenderer2) {
                hittest = (GLModelRenderer2) value;
            }

            if (ctrl != null)
                ctrl.addOnSceneBoundsChangedListener(this);
        }

        @Override
        public void onBoundsChanged(Envelope aabb, double minGsd,
                double maxGsd) {
            PointD scratch = new PointD(0d, 0d, 0d);

            GeometryCollection points = new GeometryCollection(3);
            scratch.x = aabb.minX;
            scratch.y = aabb.minY;
            scratch.z = aabb.minZ;
            if (info.localFrame != null)
                info.localFrame.transform(scratch, scratch);
            points.addGeometry(new Point(scratch.x, scratch.y, scratch.z));
            scratch.x = aabb.minX;
            scratch.y = aabb.maxY;
            scratch.z = aabb.minZ;
            if (info.localFrame != null)
                info.localFrame.transform(scratch, scratch);
            points.addGeometry(new Point(scratch.x, scratch.y, scratch.z));
            scratch.x = aabb.maxX;
            scratch.y = aabb.maxY;
            scratch.z = aabb.minZ;
            if (info.localFrame != null)
                info.localFrame.transform(scratch, scratch);
            points.addGeometry(new Point(scratch.x, scratch.y, scratch.z));
            scratch.x = aabb.maxX;
            scratch.y = aabb.minY;
            scratch.z = aabb.minZ;
            if (info.localFrame != null)
                info.localFrame.transform(scratch, scratch);
            points.addGeometry(new Point(scratch.x, scratch.y, scratch.z));
            scratch.x = aabb.minX;
            scratch.y = aabb.minY;
            scratch.z = aabb.maxZ;
            if (info.localFrame != null)
                info.localFrame.transform(scratch, scratch);
            points.addGeometry(new Point(scratch.x, scratch.y, scratch.z));
            scratch.x = aabb.minX;
            scratch.y = aabb.maxY;
            scratch.z = aabb.maxZ;
            if (info.localFrame != null)
                info.localFrame.transform(scratch, scratch);
            points.addGeometry(new Point(scratch.x, scratch.y, scratch.z));
            scratch.x = aabb.maxX;
            scratch.y = aabb.maxY;
            scratch.z = aabb.maxZ;
            if (info.localFrame != null)
                info.localFrame.transform(scratch, scratch);
            points.addGeometry(new Point(scratch.x, scratch.y, scratch.z));
            scratch.x = aabb.maxX;
            scratch.y = aabb.minY;
            scratch.z = aabb.maxZ;
            if (info.localFrame != null)
                info.localFrame.transform(scratch, scratch);
            points.addGeometry(new Point(scratch.x, scratch.y, scratch.z));

            Geometry xformed = GeometryTransformer.transform(points, info.srid,
                    4326);
            xformed.setDimension(2);

            try {
                dataStore.updateFeature(fid,
                        FeatureDataStore2.PROPERTY_FEATURE_GEOMETRY, null,
                        xformed, null, null, 0);
                final Feature update = Utils.getFeature(
                        dataStore,
                        fid);
                if (update != null)
                    this.version = update.getVersion();
            } catch (Exception e) {
                Log.e(TAG, "error", e);
            }
        }

        @Override
        public void dispose() {
            if (ctrl != null)
                ctrl.removeOnSceneBoundsChangedListener(this);
        }
    }
}
