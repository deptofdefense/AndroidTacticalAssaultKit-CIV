
package com.atakmap.android.vehicle.model.opengl;

import android.graphics.Color;
import android.graphics.PointF;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.maps.GLRubberModel;
import com.atakmap.android.vehicle.model.VehicleModel;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renderer for vehicle models
 */
public class GLVehicleModel extends GLRubberModel {

    public static final Comparator<GLVehicleModel> SORT_Z = new Comparator<GLVehicleModel>() {
        @Override
        public int compare(GLVehicleModel o1, GLVehicleModel o2) {
            return Double.compare(o2._modelAnchorPoint.z,
                    o1._modelAnchorPoint.z);
        }
    };

    private final GLInstanceData _instanceData = new GLInstanceData();

    private final Matrix _localECEF = Matrix.getIdentity();

    public GLVehicleModel(MapRenderer ctx, VehicleModel subject) {
        super(ctx, subject);
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
    public boolean hitTest(int x, int y, GeoPoint result, MapView view) {

        if (_model == null)
            return false;

        MapSceneModel sm = view.getSceneModel();

        // The current transformation matrix of the model
        Matrix localFrame;
        if (sm.mapProjection.getSpatialReferenceID() == 4978)
            localFrame = _localECEF;
        else
            localFrame = _matrix;

        int numMeshes = _model.getNumMeshes();
        for (int i = 0; i < numMeshes; i++) {

            Mesh mesh = _model.getMesh(i);

            // Note: this method accepts 'null' local frame, in which case it
            // assumes that LCS == WCS
            GeometryModel gm = Models.createGeometryModel(mesh, localFrame);
            if (sm.inverse(new PointF(x, y), result, gm) == null)
                continue;

            // adjust altitude for renderer elevation offset
            if (result.isAltitudeValid()) {
                // specify a very small offset to move towards the camera. this is
                // to prevent z-fighting when a point is placed directly on the
                // surface. currently moving ~1ft
                final double offset = 0.30d;
                moveTowardsCamera(sm, x, y, result, offset);
            }
            return true;
        }
        return false;
    }

    private static void moveTowardsCamera(MapSceneModel scene, float x, float y,
            GeoPoint gp, double meters) {
        PointD org = new PointD(x, y, -1d);
        scene.inverse.transform(org, org);
        PointD tgt = scene.mapProjection.forward(gp, null);

        double dx = org.x - tgt.x;
        double dy = org.y - tgt.y;
        double dz = org.z - tgt.z;
        final double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= d;
        dy /= d;
        dz /= d;

        PointD off;

        off = scene.mapProjection.forward(new GeoPoint(gp.getLatitude(),
                gp.getLongitude(), gp.getAltitude() + meters), null);
        final double tz = MathUtils.distance(tgt.x, tgt.y, tgt.z, off.x, off.y,
                off.z);
        off = scene.mapProjection.forward(
                computeDestinationPoint(gp, 0d, meters),
                null);
        final double tx = MathUtils.distance(tgt.x, tgt.y, tgt.z, off.x, off.y,
                off.z);
        off = scene.mapProjection.forward(
                computeDestinationPoint(gp, 90d, meters),
                null);
        final double ty = MathUtils.distance(tgt.x, tgt.y, tgt.z, off.x, off.y,
                off.z);

        tgt.x += dx * tx;
        tgt.y += dy * ty;
        tgt.z += dz * tz;

        scene.mapProjection.inverse(tgt, gp);
    }

    private static GeoPoint computeDestinationPoint(GeoPoint p, double a,
            double d) {
        GeoPoint surf = DistanceCalculations.computeDestinationPoint(p, a, d);
        return new GeoPoint(surf.getLatitude(), surf.getLongitude(),
                p.getAltitude(), p.getAltitudeReference(),
                GeoPoint.UNKNOWN, GeoPoint.UNKNOWN);
    }
}
