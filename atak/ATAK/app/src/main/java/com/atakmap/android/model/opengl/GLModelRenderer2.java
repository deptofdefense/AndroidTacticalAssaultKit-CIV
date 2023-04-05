
package com.atakmap.android.model.opengl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.opengl.GLES30;
import android.os.SystemClock;

import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIContentResolver;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.maps.tilesets.graphics.GLPendingTexture;
import com.atakmap.android.model.ModelContentHandler;
import com.atakmap.android.model.ModelContentResolver;
import com.atakmap.app.DeveloperOptions;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.Utils;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchPoint;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelFactory;
import com.atakmap.map.layer.model.ModelHitTestControl;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelSpi;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.opengl.GLMesh;
import com.atakmap.map.layer.model.opengl.MaterialManager;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;
import com.atakmap.spatial.GeometryTransformer;
import com.atakmap.util.zip.IoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class GLModelRenderer2 implements GLMapRenderable2, ModelHitTestControl {

    private final static String TAG = "GLModelRenderer";

    private final MapRenderer renderContext;
    private GLBatchPoint glpoint;
    Feature feature;
    private final FeatureDataStore2 dataStore;
    private ModelInfo sourceInfo;
    private GLPendingTexture textureLoader;
    private GLTexture texture;
    private Thread modelLoader;
    private int drawSrid;

    private Model model;
    private ModelInfo modelInfo;
    private PointD modelAnchorPoint;
    Envelope featureBounds;

    private GLTriangle.Fan _verts;
    private final GLTriangle.Fan _total;
    private boolean progressVisible = false;

    private static final float LINE_WIDTH = (float) Math
            .ceil(5f * MapView.DENSITY);
    private static final float OUTLINE_WIDTH = LINE_WIDTH
            + (6 * MapView.DENSITY);

    private GLMesh[] glMeshes;
    private final Map<Integer, GLMesh> instancedMeshes = new HashMap<>();
    private boolean meshesLocked;

    private float[] verts;

    private final MaterialManager materialManager;

    public GLModelRenderer2(MapRenderer ctx, Feature feature,
            FeatureDataStore2 dataStore, MaterialManager materialManager) {
        this.renderContext = ctx;
        this.feature = feature;
        this.dataStore = dataStore;
        this.featureBounds = this.feature.getGeometry().getEnvelope();

        this.materialManager = materialManager;

        // build a full circle for drawing as black, cheat because the function really initializes _verts.
        buildProgressArc(45, 90, 360);
        _total = _verts;
        _verts = null;
    }

    void refresh() {

    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if (!MathUtils.hasBits(renderPass, getRenderPass()))
            return;

        if (sourceInfo == null) {
            try {
                if (this.feature == null)
                    return;

                // query feature
                this.feature = Utils
                        .getFeature(this.dataStore, this.feature.getId());

                if (this.feature == null)
                    return;

                AttributeSet attrs = this.feature.getAttributes();
                if (attrs == null)
                    return;

                this.featureBounds = this.feature.getGeometry().getEnvelope();
                // load model info
                sourceInfo = GLModelLayer
                        .getModelInfo(attrs
                                .getAttributeSetAttribute("TAK.ModelInfo"));
                sourceInfo.name = this.feature.getName();
                // wrap/update GL point
                if (this.glpoint == null)
                    this.glpoint = new GLBatchPoint(view);
                this.glpoint.init(this.feature.getId(), sourceInfo.name);
                this.glpoint.setStyle(new IconPointStyle(-1,
                        "resource://" + R.drawable.icon_3d_map));

                // XXX - do something more novel with the location? e.g. point
                //       "floats" within bounding box always on screen?
                this.glpoint.setGeometry(
                        new Point(sourceInfo.location.getLongitude(),
                                sourceInfo.location.getLatitude()));
            } catch (DataStoreException e) {
                Log.d(TAG, "error", e);
                return;
            }
        }

        if (view.currentPass.drawMapResolution < 5d
                && sourceInfo.srid != -1
                && Rectangle.intersects(featureBounds.minX, featureBounds.minY,
                        featureBounds.maxX, featureBounds.maxY, view.westBound,
                        view.currentPass.southBound,
                        view.currentPass.eastBound,
                        view.currentPass.northBound)) {
            synchronized (this) {
                this.drawSrid = view.currentPass.drawSrid;
                if (this.modelInfo == null
                        || !isCompatibleSrid(view.currentPass.drawSrid,
                                this.modelInfo.srid)) {
                    if (this.modelLoader == null) {
                        this.modelLoader = new Thread(new Runnable() {
                            public void run() {
                                if (asyncLoadModel(feature, sourceInfo)
                                        && Thread
                                                .currentThread() == modelLoader)
                                    modelLoader = null;
                                renderContext.requestRefresh();
                            }
                        }, TAG + "-ModelLoader");
                        this.modelLoader.setPriority(Thread.NORM_PRIORITY);
                        this.modelLoader.start();
                    }
                } else if (drawModel(view)) {
                    if (glpoint != null)
                        glpoint.releaseLabel();
                    return;
                }
            }
        }

        view.scratch.depth.save();
        if (view.currentScene.drawTilt == 0d) {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            GLES30.glDepthMask(false);
        }
        glpoint.draw(view);
        view.scratch.depth.restore();

        if (_verts != null && progressVisible) {

            GLES20FixedPipeline.glPushMatrix();
            // XXX - this is cheating a little, grab the projected position of
            //       the point, which includes elevation offset
            view.scene.forward.transform(glpoint.posProjected,
                    view.scratch.pointD);
            GLES20FixedPipeline.glTranslatef((float) view.scratch.pointD.x,
                    (float) view.scratch.pointD.y,
                    (float) view.scratch.pointD.z);

            setColor(Color.BLACK);

            GLES20FixedPipeline.glLineWidth(OUTLINE_WIDTH);
            _total.draw(GLES20FixedPipeline.GL_LINE_STRIP);

            setColor(Color.GREEN);

            GLES20FixedPipeline.glLineWidth(LINE_WIDTH);
            _verts.draw(GLES20FixedPipeline.GL_LINE_STRIP);

            GLES20FixedPipeline.glPopMatrix();

        }

    }

    private void setColor(int color) {
        GLES20FixedPipeline.glColor4f(Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f, Color.alpha(color) / 255f);
    }

    private void buildProgressArc(final float radius, final float offsetAngle,
            float centralAngle) {

        int lineCount = 60;
        int vertCount = lineCount + 1;

        if (_verts == null) {
            _verts = new GLTriangle.Fan(2, vertCount);
            verts = new float[vertCount * 2];
        } else {
            _verts.setPointCount(vertCount);
        }

        double angle = offsetAngle * Math.PI / 180d;
        double step = (centralAngle / lineCount) * Math.PI / 180d;

        for (int i = 0; i < verts.length; i += 2) {
            float px = radius * (float) Math.cos(angle);
            float py = radius * (float) Math.sin(angle);
            verts[i] = -1 * px;
            verts[i + 1] = py;
            angle += step;
        }
        _verts.setPoints(verts);

        renderContext.requestRefresh();
    }

    private synchronized boolean drawModel(GLMapView view) {
        if (!isCompatibleSrid(view.drawSrid, this.modelInfo.srid))
            return false;

        if (glMeshes == null) {

            glMeshes = new GLMesh[model.getNumMeshes()];
            for (int i = 0; i < model.getNumMeshes(); ++i) {
                final int instanceId = model.getInstanceId(i);
                if (instanceId != Model.INSTANCE_ID_NONE) {
                    glMeshes[i] = instancedMeshes.get(instanceId);
                    if (glMeshes[i] == null) {
                        glMeshes[i] = new GLMesh(this.modelInfo,
                                model.getMesh(i, false),
                                modelAnchorPoint, this.materialManager);
                        instancedMeshes.put(instanceId, glMeshes[i]);
                    }
                } else {
                    glMeshes[i] = new GLMesh(this.modelInfo,
                            model.getMesh(i, false),
                            modelAnchorPoint, this.materialManager);
                }
            }
        }

        final int renderPass = this.getRenderPass();
        for (int i = 0; i < model.getNumMeshes(); i++)
            glMeshes[i].draw(view, renderPass, model.getTransform(i));

        return true;
    }

    @Override
    public synchronized void release() {
        if (this.glMeshes != null) {
            // if the meshes aren't locked release, otherwise they'll be
            // released when unlocked
            if (!this.meshesLocked) {
                for (int i = 0; i < model.getNumMeshes(); ++i) {
                    if (glMeshes[i] == null)
                        continue;
                    final int instanceId = model.getInstanceId(i);
                    if (instanceId == Model.INSTANCE_ID_NONE)
                        glMeshes[i].release();
                }
                for (GLMesh glMesh : instancedMeshes.values())
                    glMesh.release();
            }
            instancedMeshes.clear();
            this.glMeshes = null;
        }

        if (this.model != null) {
            // XXX - disposal is a carryover from original prototype API,
            //       individual mesh dispose handles
            this.model = null;
            this.modelInfo = null;
        }

        if (this.textureLoader != null) {
            this.textureLoader.cancel();
            this.textureLoader = null;
        }
        if (this.texture != null) {
            this.texture.release();
            this.texture = null;
        }
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SCENES;
    }

    private boolean asyncLoadModel(Feature f, ModelInfo info) {
        final ModelSpi.Callback modelLoadCallback = new ModelSpi.Callback() {
            public boolean isCanceled() {
                return false;
            }

            public boolean isProbeOnly() {
                return false;
            }

            public int getProbeLimit() {
                return 0;
            }

            public void setProbeMatch(boolean match) {
            }

            public void errorOccurred(String msg, Throwable t) {
                glpoint.init(glpoint.featureId, feature.getName()
                        + " [Error]");

                // show error icon
                glpoint.setStyle(new IconPointStyle(-1,
                        "resource://" + R.drawable.ic_3d_map_error));
                renderContext.requestRefresh();
            }

            public void progress(int progress) {
                buildProgressArc(45, 90, 360 * ((float) progress / 100));
                //glpoint.init(glpoint.featureId, feature.getName()
                //        + " [Loading Model " + progress + "%]");
                renderContext.requestRefresh();
            }
        };

        modelLoadCallback.progress(0);
        this.glpoint.init(this.glpoint.featureId, "[loading]");

        int srid = getOptimizedSrid(this.drawSrid);

        progressVisible = true;

        // check for an optimized model for the projection and load if available
        ModelInfo optimized = findOptimized(f.getAttributes(), srid);
        if (optimized != null) {
            Model retval = ModelFactory.create(optimized,
                    MemoryMappedModel.SPI.getType(),
                    modelLoadCallback);
            if (retval != null) {
                // kick off texture load as necessary
                PointD anchor = Models.findAnchorPoint(retval);
                synchronized (this) {
                    this.model = retval;
                    this.modelInfo = optimized;
                    this.modelAnchorPoint = anchor;
                }
                //asyncLoadTexture(f, info, retval);

                this.glpoint.init(this.glpoint.featureId, info.name);
                progressVisible = false;
                renderContext.requestRefresh();
                return true;
            }
        }

        // there was no optimized model, or it failed to load, go through normal process

        // load the source model
        optimized = findOptimized(f.getAttributes(), info.srid);
        long st = SystemClock.elapsedRealtime();
        Model sourceModel = null;
        if (optimized != null) {
            sourceModel = ModelFactory.create(optimized,
                    MemoryMappedModel.SPI.getType(),
                    modelLoadCallback);
            if (sourceModel != null)
                info = optimized;
        }
        if (sourceModel == null)
            sourceModel = ModelFactory.create(info, null, modelLoadCallback);
        long et = SystemClock.elapsedRealtime();
        if (sourceModel == null) {
            glpoint.init(glpoint.featureId, feature.getName()
                    + " [Error]");
            // show error icon
            glpoint.setStyle(new IconPointStyle(-1,
                    "resource://" + R.drawable.ic_3d_map_error));
            renderContext.requestRefresh();
            return false;
        }

        Log.i(TAG, "Loaded source model in " + (et - st) + "ms");

        // persist an optimized copy of the source model
        if (info != optimized) {
            st = SystemClock.elapsedRealtime();
            persistOptimized(f, sourceModel, info);
            et = SystemClock.elapsedRealtime();

            Log.i(TAG,
                    "Persisted optimized source model in " + (et - st) + "ms");
        }

        // kick off the texture load
        //asyncLoadTexture(f, info, sourceModel);

        // record bounds if current geometry is a point
        if (f.getGeometry() instanceof Point) {
            Envelope aabb = sourceModel.getAABB();
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
                if (feature == null) {
                    Log.w(TAG, "Model " + f.getName()
                            + " has null feature - possibly deleted during import");
                    return false;
                }
                this.dataStore.updateFeature(feature.getId(),
                        FeatureDataStore2.PROPERTY_FEATURE_GEOMETRY, null,
                        xformed, null, null, 0);
                feature = Utils.getFeature(dataStore,
                        feature.getId());
                if (feature != null) {
                    featureBounds = feature.getGeometry().getEnvelope();

                    // Update the model handler(s)
                    long fsid = feature.getFeatureSetId();
                    List<URIContentResolver> resolvers = URIContentManager
                            .getInstance().getResolvers();
                    for (URIContentResolver r : resolvers) {
                        if (!(r instanceof ModelContentResolver))
                            continue;
                        ModelContentHandler h = ((ModelContentResolver) r)
                                .getHandler(fsid);
                        if (h != null)
                            h.addFeatureBounds(featureBounds);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "error", e);
            }
        }

        srid = getOptimizedSrid(this.drawSrid);

        // transform the model as necessary
        ModelInfo mInfo = new ModelInfo(info);
        Model m = sourceModel;
        if (!isCompatibleSrid(srid, mInfo.srid)) {
            this.glpoint.init(this.glpoint.featureId, "[reprojecting]");

            mInfo.srid = srid;
            mInfo.localFrame = null;
            Model xformed = Models.transform(info, m, mInfo,
                    new Models.OnTransformProgressListener() {
                        @Override
                        public void onTransformProgress(int progress) {
                            buildProgressArc(45, 90,
                                    360 * ((float) progress / 100));
                            //glpoint.init(glpoint.featureId, feature.getName()
                            //        + " [Reprojecting " + progress + "%]");
                            renderContext.requestRefresh();
                        }
                    });
            m.dispose();
            m = xformed;
            if (m == null) {
                glpoint.init(glpoint.featureId, feature.getName()
                        + " [Error]");
                // show error icon
                glpoint.setStyle(new IconPointStyle(-1,
                        "resource://" + R.drawable.ic_3d_map_error));
                renderContext.requestRefresh();
                return false;
            }
        }

        PointD anchor = Models.findAnchorPoint(m);

        synchronized (this) {
            this.modelInfo = mInfo;
            this.model = m;
            this.modelAnchorPoint = anchor;

            renderContext.requestRefresh();
        }

        this.glpoint.init(this.glpoint.featureId, info.name);
        buildProgressArc(45, 90, 0);
        progressVisible = false;

        // store optimized representation
        if (sourceModel != m) {
            persistOptimized(f, m, mInfo);
        }

        return true;
    }

    private void persistOptimized(Feature f, Model m, ModelInfo mInfo) {
        FileChannel fos = null;
        try {
            File optimizedFile = FileSystemUtils
                    .getItem("Databases/models.db/resources/"
                            + feature.getId()
                            + "/optimized-models/"
                            + mInfo.srid);
            if (!IOProviderFactory.mkdirs(optimizedFile.getParentFile())) {
                Log.e(TAG, "cannot create directory for: "
                        + optimizedFile.getParentFile());
            }
            fos = IOProviderFactory.getChannel(optimizedFile, "rw");
            MemoryMappedModel.write(m, fos);

            AttributeSet attrs = f.getAttributes();
            AttributeSet modelAttrs = attrs
                    .getAttributeSetAttribute("TAK.ModelInfo");
            AttributeSet optimizedAttrs;
            if (!modelAttrs.containsAttribute("optimized"))
                modelAttrs.setAttribute("optimized", new AttributeSet());
            optimizedAttrs = modelAttrs.getAttributeSetAttribute("optimized");

            AttributeSet sridAttrs = new AttributeSet();
            if (mInfo.type != null)
                sridAttrs.setAttribute("type", mInfo.type);
            sridAttrs.setAttribute("uri", optimizedFile.getAbsolutePath());
            if (mInfo.localFrame != null) {
                double[] mx = new double[16];
                mInfo.localFrame.get(mx);
                sridAttrs.setAttribute("localFrame", mx);
            }

            optimizedAttrs.setAttribute(String.valueOf(mInfo.srid), sridAttrs);

            try {
                this.dataStore.updateFeature(feature.getId(),
                        FeatureDataStore2.PROPERTY_FEATURE_ATTRIBUTES, null,
                        null, null, attrs,
                        FeatureDataStore2.UPDATE_ATTRIBUTES_SET);
                feature = Utils.getFeature(dataStore,
                        feature.getId());
                if (feature != null)
                    featureBounds = feature.getGeometry().getEnvelope();
            } catch (DataStoreException e) {
                Log.d(TAG, "error", e);
            }
        } catch (Throwable t) {
            Log.e(TAG, "error", t);
        } finally {
            IoUtils.close(fos);
        }
    }

    private static ModelInfo findOptimized(AttributeSet attrs, int srid) {
        if (DeveloperOptions.getIntOption(
                "glmodelrenderer.optimized-models-disabled", 0) != 0)
            return null;

        AttributeSet modelInfo = attrs
                .getAttributeSetAttribute("TAK.ModelInfo");
        if (modelInfo == null)
            return null;
        ModelInfo sourceInfo = GLModelLayer.getModelInfo(modelInfo);
        if (sourceInfo == null)
            return null;
        if (!modelInfo.containsAttribute("optimized"))
            return null;

        AttributeSet optimized = modelInfo
                .getAttributeSetAttribute("optimized");
        if (optimized == null)
            return null;

        if (!optimized.containsAttribute(String.valueOf(srid)))
            return null;

        optimized = optimized.getAttributeSetAttribute(String.valueOf(srid));

        ModelInfo retval = new ModelInfo(sourceInfo);
        if (optimized.containsAttribute("type"))
            retval.type = optimized.getStringAttribute("type");
        retval.uri = optimized.getStringAttribute("uri");
        if (optimized.containsAttribute("localFrame")) {
            double[] mx = optimized.getDoubleArrayAttribute("localFrame");
            retval.localFrame = new Matrix(mx[0], mx[1], mx[2], mx[3], mx[4],
                    mx[5], mx[6], mx[7], mx[8], mx[9], mx[10], mx[11], mx[12],
                    mx[13], mx[14], mx[15]);
        }
        retval.srid = srid;

        return retval;
    }

    private static boolean load(Properties props, File f, boolean logEx) {
        final boolean exists = IOProviderFactory.exists(f);
        if (!exists)
            return false;

        try (FileInputStream fis = IOProviderFactory.getInputStream(f)) {
            props.load(fis);
            return true;
        } catch (IOException e) {
            if (logEx)
                Log.w(TAG, "Failed to load properties", e);
            return false;
        }
    }

    private static boolean store(Properties props, File f, boolean logEx) {
        if (!IOProviderFactory.exists(f.getParentFile()))
            if (!IOProviderFactory.mkdirs(f.getParentFile()))
                Log.w(TAG,
                        "Could not create the directory: " + f.getParentFile());

        try (OutputStream os = IOProviderFactory.getOutputStream(f)) {
            props.store(os, null);
            return true;
        } catch (IOException e) {
            if (logEx)
                Log.w(TAG, "Failed to store properties", e);
            return false;
        }
    }

    @Override
    public boolean hitTest(float screenX, float screenY,
            GeoPoint result) {

        final GLMesh[] meshes;
        final MapSceneModel sm = MapView.getMapView().getSceneModel();
        synchronized (this) {
            if (this.glMeshes == null)
                return false;
            meshes = this.glMeshes;
            this.meshesLocked = true;
            if (!isCompatibleSrid(sm.mapProjection.getSpatialReferenceID(),
                    this.modelInfo.srid))
                return false;
        }

        boolean retval = false;

        for (GLMesh mesh : meshes) {
            // XXX - need to find closest hit
            if (mesh != null && mesh.hitTest(sm, screenX, screenY, result)) {
                retval = true;
                break;
            }
        }

        synchronized (this) {
            if (meshes != this.glMeshes) {
                renderContext.queueEvent(new Runnable() {
                    public void run() {
                        // XXX - instanced meshes

                        for (GLMesh mesh : meshes)
                            if (mesh != null)
                                mesh.release();
                    }
                });
            }
            this.meshesLocked = false;
        }
        return retval;
    }

    public static class TextureLoaderImpl implements Callable<Bitmap> {
        File resourceDir;
        int maxTexSize;
        String textureUri;
        final Object lock;

        public TextureLoaderImpl(File resourceDir, int maxTexSize,
                String textureUri, Object lock) {
            this.resourceDir = resourceDir;
            this.maxTexSize = maxTexSize;
            this.textureUri = textureUri;
            this.lock = lock;
        }

        @Override
        public Bitmap call() {
            // decode bitmap bounds

            long s, e;
            BitmapFactory.Options opts;

            File resourceTextures = new File(resourceDir, "textures");
            File textureDir;
            synchronized (lock) {
                // obtain resource map
                Properties resourceMapping = new Properties();
                File f = new File(resourceDir, "resource-mapping");
                if (IOProviderFactory.exists(f)) {
                    load(resourceMapping, f, true);
                } else if (IOProviderFactory.exists(resourceTextures)) {
                    Log.w(TAG, "Missing resource mapping");
                }

                // lookup texture URI
                final String alias = resourceMapping.getProperty(textureUri);
                if (alias != null) {
                    textureDir = new File(resourceTextures, alias);
                } else {
                    //create mapping if necessary
                    try {
                        if (!IOProviderFactory.exists(resourceTextures)) {
                            if (!IOProviderFactory.mkdirs(resourceTextures)) {
                                Log.d(TAG,
                                        "could not make the resource textures: "
                                                + resourceTextures);
                            }
                        }
                        textureDir = FileSystemUtils.createTempDir(
                                new File(textureUri).getName(), "",
                                resourceTextures);
                    } catch (IOException t) {
                        Log.w(TAG,
                                "Failed to create resource cache directory for "
                                        + textureUri,
                                t);
                        textureDir = null;
                    }

                    // update resource mapping
                    if (textureDir != null) {
                        resourceMapping.setProperty(textureUri,
                                textureDir.getName());
                        store(resourceMapping, f, true);
                    }
                }

                File metadataFile = null;
                if (textureDir != null)
                    metadataFile = new File(textureDir, "metadata");

                boolean metadataBoundsDecode = false;

                s = SystemClock.elapsedRealtime();
                opts = new BitmapFactory.Options();
                do {
                    Properties metadata = new Properties();
                    if (metadataFile != null
                            && load(metadata, metadataFile, true)) {
                        final String width = metadata.getProperty("width",
                                null);
                        final String height = metadata.getProperty("height",
                                null);
                        if (width != null && height != null) {
                            try {
                                opts.outWidth = Integer.parseInt(width);
                                opts.outHeight = Integer.parseInt(height);
                                metadataBoundsDecode = true;
                                break;
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        // fall-through
                    }

                    opts.inJustDecodeBounds = true;
                    decodeUri(textureUri, opts);
                    if (metadataFile != null) {
                        metadata.put("width", String.valueOf(opts.outWidth));
                        metadata.put("height", String.valueOf(opts.outHeight));
                        store(metadata, metadataFile, true);
                    }
                } while (false);
                e = SystemClock.elapsedRealtime();

                Log.d(TAG, "Decode texture bounds in " + (e - s)
                        + "ms [using metadata=" + metadataBoundsDecode + "]");
                Log.d(TAG,
                        "Texture bounds " + opts.outWidth + "x"
                                + opts.outHeight);
            }

            String decodeUri = textureUri;

            // if the bounds are greater than the desired texture size
            if (opts.outWidth > maxTexSize || opts.outHeight > maxTexSize) {
                if (textureDir != null) {
                    if (!IOProviderFactory.exists(textureDir)) {
                        if (!IOProviderFactory.mkdirs(textureDir)) {
                            Log.d(TAG, "could not make: " + textureDir);
                        }
                    }
                }

                // determine target subsample factor
                int rset = (int) Math.ceil(Math
                        .log((double) Math.max(opts.outHeight, opts.outWidth)
                                / (double) maxTexSize)
                        / Math.log(2));

                // see if we already have a subsampled version
                File rsetFile = textureDir != null
                        ? new File(textureDir, String.valueOf(rset))
                        : null;
                if (rsetFile != null && IOProviderFactory.exists(rsetFile)) {
                    Log.d(TAG, "Using pre-subsampled texture " + rsetFile);
                    decodeUri = rsetFile.getAbsolutePath();
                } else {
                    // find the smallest subsampled texture greater than our factor and subsample to the target factor
                    int fromRset = 0;
                    if (textureDir != null) {
                        for (int i = rset - 1; i > 0; i--) {
                            rsetFile = new File(textureDir,
                                    String.valueOf(rset));
                            if (!IOProviderFactory.exists(rsetFile))
                                continue;
                            decodeUri = rsetFile.getAbsolutePath();
                            fromRset = i;
                            break;
                        }
                    }

                    // sample to the target factor
                    opts = new BitmapFactory.Options();
                    opts.inSampleSize = 1 << (rset - fromRset);
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;

                    s = SystemClock.elapsedRealtime();
                    Bitmap b = decodeUri(decodeUri, opts);
                    e = SystemClock.elapsedRealtime();
                    Log.d(TAG, "Subsampled texture in " + (e - s) + "ms");
                    Log.d(TAG, "Texture bounds " + b.getWidth() + "x"
                            + b.getHeight());

                    // XXX - save the subsampled version
                    if (textureDir != null) {
                        try (FileOutputStream fos = IOProviderFactory
                                .getOutputStream(rsetFile)) {

                            s = SystemClock.elapsedRealtime();
                            b.compress(Bitmap.CompressFormat.JPEG, 75, fos);
                            e = SystemClock.elapsedRealtime();
                            Log.d(TAG, "Saved subsampled texture in " + (e - s)
                                    + "ms");
                        } catch (IOException t) {
                            Log.w(TAG,
                                    "Failed to save subsampled texture version",
                                    t);
                        }
                    }

                    return b;
                }

            }

            opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return decodeUri(decodeUri, opts);
        }

        static Bitmap decodeUri(String uri, BitmapFactory.Options opts) {
            File f = new File(uri);
            if (IOProviderFactory.exists(f))
                try (FileInputStream fis = IOProviderFactory
                        .getInputStream(f)) {
                    return BitmapFactory.decodeStream(fis, null, opts);
                } catch (IOException e) {
                    Log.w(TAG,
                            "Failed to load cached texture, reloading from source",
                            e);
                }

            //if (uri.contains(".zip") || uri.contains(".kmz")) {
            try {
                f = new ZipVirtualFile(uri);
                if (IOProviderFactory.exists(f)) {
                    InputStream stream = null;
                    try {
                        stream = ((ZipVirtualFile) f).openStream();
                        return BitmapFactory.decodeStream(stream, null,
                                opts);
                    } finally {
                        if (stream != null)
                            stream.close();
                    }
                }
            } catch (Throwable ignored) {
            }
            //}

            // last ditch effort -- we've tried going through the IO
            // abstraction first, try normal IO
            return BitmapFactory.decodeFile(uri, opts);
        }
    }

    public static class TextureLoader implements MaterialManager.TextureLoader {
        MapRenderer renderContext;
        File resourceDir;
        int maxTexSize;

        public TextureLoader(MapRenderer renderContext, File resourceDir,
                int maxTexSize) {
            this.renderContext = renderContext;
            this.resourceDir = resourceDir;
            this.maxTexSize = maxTexSize;
        }

        @Override
        public FutureTask<Bitmap> load(String textureUri) {
            final FutureTask<Bitmap> loader = new FutureTask<>(
                    new TextureLoaderImpl(resourceDir, maxTexSize, textureUri,
                            this));
            GLRenderGlobals.get(this.renderContext).getBitmapLoader()
                    .loadBitmap(loader, GLBitmapLoader.QueueType.LOCAL);
            return loader;
        }
    }

    static int getOptimizedSrid(int drawSrid) {
        if (drawSrid == 4978)
            drawSrid = 4326;
        return drawSrid;
    }

    static boolean isCompatibleSrid(int drawSrid, int modelSrid) {
        return getOptimizedSrid(drawSrid) == modelSrid;
    }
}
