
package com.atakmap.android.vehicle.model.opengl;

import android.graphics.Color;
import android.graphics.PointF;

import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.imagecapture.CanvasHelper;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.rubbersheet.maps.GLRubberModel;
import com.atakmap.android.vehicle.model.VehicleModel;
import com.atakmap.android.vehicle.model.VehicleModelInfo;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renderer for vehicle models
 */
public class GLVehicleModel extends GLRubberModel implements
        MapItem.OnMetadataChangedListener {

    public static final Comparator<GLVehicleModel> SORT_Z = new Comparator<GLVehicleModel>() {
        @Override
        public int compare(GLVehicleModel o1, GLVehicleModel o2) {
            return Double.compare(o2._modelAnchorPoint.z,
                    o1._modelAnchorPoint.z);
        }
    };

    private final VehicleModel _subject;
    private final GLInstanceData _instanceData = new GLInstanceData();
    private final Matrix _localECEF = Matrix.getIdentity();

    // Used for outline hit testing
    private DoubleBuffer _outlinePts;
    private boolean _outlineInvalid;
    private boolean _showOutline;
    private double _heading;

    public GLVehicleModel(MapRenderer ctx, VehicleModel subject) {
        super(ctx, subject);
        _subject = subject;
    }

    @Override
    public void startObserving() {
        super.startObserving();
        _subject.addOnMetadataChangedListener("outline", this);
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        _subject.removeOnMetadataChangedListener("outline", this);
    }

    @Override
    public synchronized void release() {
        super.release();
        freeOutlineBuffer();
    }

    public List<Mesh> getMeshes() {
        List<Mesh> ret = new ArrayList<>();
        if (_model == null)
            return ret;

        int meshCount = _model.getNumMeshes();
        for (int i = 0; i < meshCount; i++) {
            Mesh mesh = _model.getMesh(i);
            if (mesh != null)
                ret.add(mesh);
        }

        return ret;
    }

    public ModelInfo getModelInfo() {
        return _modelInfo;
    }

    /**
     * Get parameters for this vehicle's mesh instance data
     * @return Instance data
     */
    public GLInstanceData getInstanceData() {
        return _instanceData;
    }

    @Override
    public void onMetadataChanged(MapItem item, String field) {
        if (field.equals("outline"))
            requestRefresh();
    }

    @Override
    protected void onRefresh() {
        // Set anchor center point
        _instanceData.setAnchor(_anchorPoint);

        // Set local frame
        updateECEF();
        _instanceData.setLocalFrame(_matrix, _localECEF);

        // Set color mod
        _instanceData.setColor(Color.red(_color) / 255f,
                Color.green(_color) / 255f,
                Color.blue(_color) / 255f, _alpha);

        // Flag as needs update
        _instanceData.setDirty(true);

        _heading = _subject.getHeading();
        _showOutline = _subject.showOutline();
        if (_showOutline)
            _outlineInvalid = true;
        else
            freeOutlineBuffer();
    }

    /**
     * Free the point buffer used for the calculated geodetic outline
     */
    private void freeOutlineBuffer() {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                _outlineInvalid = false;
                Unsafe.free(_outlinePts);
                _outlinePts = null;
            }
        });
    }

    private void updateECEF() {
        _localECEF.setToIdentity();

        // LLA coords -> ECEF coords
        PointD p = new PointD(0, 0, 0);
        GeoPoint gp = _anchorPoint;
        if (!gp.isAltitudeValid())
            gp = new GeoPoint(gp.getLatitude(), gp.getLongitude(), 0);
        ECEFProjection.INSTANCE.forward(gp, p);
        _localECEF.translate(p.x, p.y, p.z);

        // construct ENU -> ECEF
        double phi = Math.toRadians(gp.getLatitude());
        double lambda = Math.toRadians(gp.getLongitude());

        double pCos = Math.cos(phi);
        double pSin = Math.sin(phi);
        double lCos = Math.cos(lambda);
        double lSin = Math.sin(lambda);

        Matrix enu2ecef = new Matrix(
                -lSin, -pSin * lCos, pCos * lCos, 0d,
                lCos, -pSin * lSin, pCos * lSin, 0d,
                0, pCos, pSin, 0d,
                0d, 0d, 0d, 1d);

        _localECEF.concatenate(enu2ecef);

        // Apply scale (almost always [1, 1, 1], but whatever)
        double[] scale = _subject.getModelScale();
        _localECEF.scale(scale[0], scale[1], scale[2]);

        // Apply rotation
        double[] r = _subject.getModelRotation();
        _localECEF.rotate(Math.toRadians(r[0]), 1.0f, 0.0f, 0.0f);
        _localECEF.rotate(Math.toRadians(360d - r[1]), 0.0f, 0.0f, 1.0f);
        _localECEF.rotate(Math.toRadians(r[2]), 0.0f, 1.0f, 0.0f);
    }

    @Override
    protected boolean getClickable() {
        return clickable && _showOutline || super.getClickable();
    }

    /**
     * Update the latest map scene model used to render this model
     * Only should be called by {@link GLVehicleModelLayer} during draw ops
     *
     * @param scene Map scene model
     */
    void updateScene(MapSceneModel scene) {
        _scene = scene;
    }

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        if (_showOutline) {
            HitTestResult result = hitTestOutline(params);
            if (result != null)
                return result;
        }
        if (shouldRender()) {
            HitTestResult result = hitTestModel(params);
            if (result != null)
                return result;
        }
        return null;
    }

    /**
     * Perform a hit test against the 3D model geometry
     * @param params Hit test parameters
     * @return Result if hit
     */
    private HitTestResult hitTestModel(HitTestQueryParameters params) {
        MapSceneModel localScene = _scene;
        Model localModel = _model;

        if (localModel == null || localScene == null || params == null)
            return null;

        // The current transformation matrix of the model
        Matrix localFrame;
        if (localScene.mapProjection.getSpatialReferenceID() == 4978)
            localFrame = _localECEF;
        else
            localFrame = _matrix;

        final GeoPoint geo = GeoPoint.createMutable();
        final int numMeshes = localModel.getNumMeshes();
        for (int i = 0; i < numMeshes; i++) {
            final Mesh mesh = localModel.getMesh(i);
            // Note: this method accepts 'null' local frame, in which case it
            // assumes that LCS == WCS
            final GeometryModel gm = Models.createGeometryModel(mesh,
                    localFrame);
            if (localScene.inverse(params.point, geo, gm) == null)
                continue;
            return new HitTestResult(_subject, geo);
        }

        return null;
    }

    /**
     * Perform a hit test against the vehicle's terrain outline
     * @param params Hit test parameters
     * @return Result if outline hit
     */
    private HitTestResult hitTestOutline(HitTestQueryParameters params) {
        if (!params.bounds.intersects(this.bounds))
            return null;

        // Check if outline needs to be updated
        if (_outlineInvalid) {

            VehicleModelInfo vInfo = _subject.getVehicleInfo();
            if (vInfo == null)
                return null;

            List<PointF> points = vInfo.getOutline(null);
            if (points == null)
                return null;

            // Allocate buffer for geodetically calculated outline
            int limit = points.size() * 2;
            if (_outlinePts == null || _outlinePts.capacity() < limit) {
                Unsafe.free(_outlinePts);
                _outlinePts = Unsafe.allocateDirect(limit, DoubleBuffer.class);
            }
            _outlinePts.clear();
            _outlinePts.limit(limit);

            // Calculate geodetic outline
            PointF pCen = new PointF();
            for (PointF p : points) {
                double a = CanvasHelper.angleTo(pCen, p);
                double d = CanvasHelper.length(pCen, p);
                a += _heading + 180;
                GeoPoint gp = GeoCalculations.pointAtDistance(_anchorPoint, a,
                        d);
                _outlinePts.put(gp.getLongitude());
                _outlinePts.put(gp.getLatitude());
            }

            _outlineInvalid = false;
        }

        if (_outlinePts == null)
            return null;

        // Hit test against geodetic outline
        GeoPoint cur = GeoPoint.createMutable();
        GeoPoint last = GeoPoint.createMutable();
        MutableGeoBounds lineBounds = new MutableGeoBounds();
        int limit = _outlinePts.limit();
        for (int i = 0; i < limit; i += 2) {
            double lng = _outlinePts.get(i);
            double lat = _outlinePts.get(i + 1);

            cur.set(lat, lng);

            // Check if the point is contained within the hit bounds
            if (params.bounds.contains(cur))
                return new HitTestResult(_subject, cur);

            // Line hit check
            if (i > 0) {
                lineBounds.set(last.getLatitude(), last.getLongitude(), lat,
                        lng);
                if (params.bounds.intersects(lineBounds)) {
                    Vector2D nearest = Vector2D.nearestPointOnSegment(
                            FOVFilter.geo2Vector(params.geo),
                            FOVFilter.geo2Vector(last),
                            FOVFilter.geo2Vector(cur));
                    float nx = (float) nearest.x;
                    float ny = (float) nearest.y;

                    // Check if the nearest point is within hit bounds
                    GeoPoint pt = new GeoPoint(ny, nx);
                    if (params.bounds.contains(pt))
                        return new HitTestResult(_subject, pt);
                }
            }

            last.set(cur);
        }

        return null;
    }
}
