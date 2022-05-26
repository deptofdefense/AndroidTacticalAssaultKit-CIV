
package com.atakmap.android.rubbersheet.data.create;

import android.os.SystemClock;

import com.atakmap.android.rubbersheet.data.ModelProjection;
import com.atakmap.android.rubbersheet.data.ModelTransformListener;
import com.atakmap.android.rubbersheet.data.RubberModelData;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelFactory;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelInfoFactory;
import com.atakmap.map.layer.model.ModelSpi;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.VertexDataLayout;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * Model loading - should be called on an asynchronous thread
 */
public class ModelLoader implements ModelSpi.Callback {

    private static final String TAG = "ModelLoader";

    private final File _file;
    private final String _subModel;
    private final ModelProjection _projection;
    private final Callback _callback;

    private Matrix _transformMat;
    private int _totalModels = 1;
    private int _modelProgress = 0;

    public ModelLoader(File file, String subModel, ModelProjection projection,
            Callback cb) {
        _file = file;
        _subModel = subModel;
        _projection = projection;
        _callback = cb;
    }

    public ModelLoader(RubberModelData data, Callback cb) {
        this(data.file, data.subModel, data.projection, cb);
    }

    /**
     * Set the transform matrix to be applied to the model after center alignment
     * with the ground
     * @param transformMat Transform matrix
     */
    public void setTransform(Matrix transformMat) {
        _transformMat = transformMat;
    }

    public boolean load() {
        if (!FileSystemUtils.isFile(_file)) {
            Log.e(TAG, "File doesn't exist: " + _file);
            return false;
        }

        // Load the model info list
        long start = SystemClock.elapsedRealtime();
        String name = _file.getName();
        Set<ModelInfo> infoList = ModelInfoFactory.create(
                _file.getAbsolutePath());
        if (infoList == null || infoList.isEmpty()) {
            Log.w(TAG, "Model info factory returned empty list for " + _file);
            return false;
        }

        // User canceled
        if (isCanceled())
            return false;

        Log.d(TAG, "Took " + (SystemClock.elapsedRealtime() - start)
                + "ms to read model info list for " + name);

        _totalModels = infoList.size();
        if (_subModel != null) {
            for (ModelInfo info : infoList) {
                if (_subModel.equals(info.uri)) {
                    infoList = Collections.singleton(info);
                    _totalModels = 1;
                    break;
                }
            }
        }

        _modelProgress = -1;
        for (ModelInfo info : infoList) {
            _modelProgress++;

            // Load the model geometry
            start = SystemClock.elapsedRealtime();
            Model model = ModelFactory.create(info, null, this);
            if (model == null)
                continue;

            if (isCanceled())
                return false;

            name = info.name;
            Log.d(TAG, "Took " + (SystemClock.elapsedRealtime() - start)
                    + "ms to read model for " + name);

            // Check if we need to transform the model so it's centered properly
            Envelope e = model.getAABB();
            if (e == null)
                continue;

            if (Double.isNaN(e.minX) || Double.isNaN(e.maxX)
                    || Double.isNaN(e.minY) || Double.isNaN(e.maxY)
                    || Double.isNaN(e.minZ) || Double.isNaN(e.maxZ))
                continue;

            PointD offset = new PointD((e.minX + e.maxX) / 2.0,
                    (e.minY + e.maxY) / 2.0, e.minZ);

            boolean needsCenter = Math.abs(offset.x) > 0.01
                    || Math.abs(offset.y) > 0.01
                    || Math.abs(offset.z) > 0.01;

            // Clear out the local frame since we'll be manipulating it anyway
            info.localFrame = Matrix.getIdentity();

            // Model needs to be transformed
            if (needsCenter || _projection != ModelProjection.ENU) {
                start = SystemClock.elapsedRealtime();
                ModelInfo trInfo = new ModelInfo(info);
                trInfo.localFrame = Matrix.getIdentity();

                // Flip Y and Z axis
                if (_projection == ModelProjection.ENU_FLIP_YZ) {
                    trInfo.localFrame.rotate(Math.toRadians(270), 1, 0, 0);
                    offset.y = (e.minZ + e.maxZ) / -2;
                    offset.z = e.minY;
                }

                // Convert latitude and longitude to ENU
                if (_projection == ModelProjection.LLA) {
                    projectLLA(model, trInfo.localFrame);
                    if (trInfo.location == null)
                        trInfo.location = new GeoPoint(offset.y, offset.x,
                                offset.z);
                    needsCenter = false;
                } else if (trInfo.location != null) {
                    // Location fix
                    Matrix trOffset = Matrix.getIdentity();
                    if (info.scale != null)
                        trOffset.scale(info.scale.x, info.scale.y);
                    if (info.rotation != null)
                        trOffset.rotate(Math.toRadians(-info.rotation.y),
                                0, 0, 1);
                    PointD loc = new PointD(offset);
                    trOffset.transform(offset, loc);
                    trInfo.location = GeoCalculations.pointAtDistance(
                            trInfo.location,
                            Math.toDegrees(Math.atan2(loc.x, loc.y)),
                            Math.hypot(loc.x, loc.y));
                }

                // Center the model with the bottom level to the ground
                if (needsCenter)
                    trInfo.localFrame.translate(offset.x, offset.y);

                // Apply transform matrix if specified
                if (_transformMat != null)
                    trInfo.localFrame.concatenate(_transformMat);

                // TODO: maybe use the new aabb of the transformed model to find the location offset
                model = Models.transform(info, model, trInfo,
                        new ModelTransformListener(model,
                                _onTransformProgress));

                info = trInfo;
                info.localFrame = null; // Used by the renderer later

                // Recalculate the AABB based on the model's transformed points
                Envelope.Builder eb = new Envelope.Builder();
                PointD pd = new PointD(0, 0, 0);
                int meshCount = model.getNumMeshes();
                for (int m = 0; m < meshCount; m++) {
                    Mesh mesh = model.getMesh(m);
                    if (mesh == null)
                        continue;

                    // Make sure the mesh has position data
                    VertexDataLayout vdl = mesh.getVertexDataLayout();
                    if (!MathUtils.hasBits(vdl.attributes,
                            Mesh.VERTEX_ATTR_POSITION))
                        continue;

                    // For every vertex in the model, calculate the envelope
                    int vCount = mesh.getNumVertices();
                    for (int v = 0; v < vCount; v++) {
                        mesh.getPosition(v, pd);
                        eb.add(pd.x, pd.y, pd.z);
                    }
                }

                // Set the model's AABB to the calculated value
                e = eb.build();
                Envelope aabb = model.getAABB();
                aabb.minX = e.minX;
                aabb.minY = e.minY;
                aabb.minZ = e.minZ;
                aabb.maxX = e.maxX;
                aabb.maxY = e.maxY;
                aabb.maxZ = e.maxZ;

                Log.d(TAG, "Took " + (SystemClock.elapsedRealtime() - start)
                        + "ms to transform " + name);

                if (isCanceled())
                    return false;
            }

            // Always use 4326 projection (for now, not sure if this needs to be preserved)
            info.srid = 4326;

            // Always use absolute - relative renders incorrectly
            info.altitudeMode = ModelInfo.AltitudeMode.Absolute;

            _callback.onLoad(info, model);
        }

        return true;
    }

    @Override
    public boolean isCanceled() {
        return _callback != null && _callback.isCancelled();
    }

    @Override
    public boolean isProbeOnly() {
        return false;
    }

    @Override
    public int getProbeLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void setProbeMatch(boolean match) {
    }

    @Override
    public void errorOccurred(final String msg, Throwable t) {
        Log.e(TAG, "Failed to read model: " + msg, t);
    }

    @Override
    public void progress(int progress) {
        if (_callback != null) {
            float progPerStage = 1f / _totalModels;
            float floorProg = _modelProgress * (progPerStage * 100);
            float prog = progress * progPerStage;
            _callback.onProgress(Math.round(floorProg + prog));
        }
    }

    // Progress callback (percentage)
    public interface Callback {
        void onProgress(int progress);

        boolean isCancelled();

        void onLoad(ModelInfo info, Model model);
    }

    // XXX - Proguard apparently confuses different methods from different
    // interfaces with the same arguments... trash
    private final Models.OnTransformProgressListener _onTransformProgress = new Models.OnTransformProgressListener() {
        @Override
        public void onTransformProgress(int progress) {
            /*if (_callback != null)
                _callback.onProgress(90 + Math.round(progress * 0.1f));*/
        }
    };

    /**
     * Convert lon/lat/alt vertices to east/north/up coordinates
     * @param model Model data
     * @param mx Matrix to apply transforms
     */
    private void projectLLA(Model model, Matrix mx) {
        Envelope e = model.getAABB();
        if (e == null)
            return;

        double centerLng = (e.minX + e.maxX) / 2;
        double centerLat = (e.minY + e.maxY) / 2;

        double metersPerDegLat = GeoCalculations
                .approximateMetersPerDegreeLatitude(centerLat);
        double metersPerDegLng = GeoCalculations
                .approximateMetersPerDegreeLongitude(centerLat);

        mx.translate(centerLng, centerLat, e.minZ);
        mx.scale(1d / metersPerDegLng, 1d / metersPerDegLat, 1d);
    }
}
