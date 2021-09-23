
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.maps.SensorFOV.OnMetricsChangedListener;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.Shape.OnPointsChangedListener;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchPolygon;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;

/**
 * Used to draw a {@link SensorFOV} cone on the map
 */
public class GLSensorFOV extends GLShape2 implements OnMetricsChangedListener,
        OnPointsChangedListener {

    private final static int SLICES_PER_90 = 8;

    GeoPoint _point;
    float _azimuth = 0f;
    float _fov = 10f;

    float _extent;
    private final GLBatchPolygon _poly;
    private SurfaceRendererControl _surfaceControl;

    public GLSensorFOV(MapRenderer surface, SensorFOV subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE);
        _poly = new GLBatchPolygon(surface);
        _point = subject.getPoint().get();
        _setMetrics(subject.getAzimuth(), subject.getFOV(),
                subject.getExtent());
        refreshStyle();
    }

    private void refreshStyle() {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                boolean stroke = strokeWeight > 0;
                ArrayList<Style> composite = new ArrayList<>();
                composite.add(new BasicFillStyle(fillColor));
                if (stroke)
                    composite.add(
                            new BasicStrokeStyle(strokeColor, strokeWeight));
                Style s = new CompositeStyle(composite.toArray(new Style[0]));
                _poly.setStyle(s);

                if (_surfaceControl != null)
                    _surfaceControl.markDirty(new Envelope(bounds.getWest(),
                            bounds.getSouth(), 0d, bounds.getEast(),
                            bounds.getNorth(), 0d), true);
            }
        });
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (!MathUtils.hasBits(renderPass, getRenderPass()))
            return;
        if (_surfaceControl == null)
            _surfaceControl = ortho.getControl(SurfaceRendererControl.class);
        _poly.draw(ortho);
    }

    @Override
    public void startObserving() {
        final SensorFOV sensor = (SensorFOV) this.subject;
        super.startObserving();

        this.onMetricsChanged(sensor);

        sensor.addOnMetricsChangedListener(this);
        sensor.addOnPointsChangedListener(this);
    }

    @Override
    public void stopObserving() {
        final SensorFOV sensor = (SensorFOV) this.subject;
        super.stopObserving();
        sensor.removeOnPointsChangedListener(this);
        sensor.removeOnMetricsChangedListener(this);
    }

    @Override
    public void onMetricsChanged(SensorFOV fov) {
        final float azimuth = fov.getAzimuth();
        final float f = fov.getFOV();
        final float extent = fov.getExtent();

        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                _setMetrics(azimuth, f, extent);
            }
        });
    }

    @Override
    public void onPointsChanged(Shape fov) {
        final SensorFOV sfov = (SensorFOV) fov;
        final GeoPoint point = sfov.getPoint().get();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                _point = point;
                updatePolygon();
                // update the bounds and notify the listeners
                sfov.getBounds(bounds);
                dispatchOnBoundsChanged();
            }
        });
    }

    @Override
    public void onFillColorChanged(Shape shape) {
        super.onFillColorChanged(shape);
        refreshStyle();
    }

    @Override
    public void onStrokeColorChanged(Shape shape) {
        super.onStrokeColorChanged(shape);
        refreshStyle();
    }

    @Override
    public void onStrokeWeightChanged(Shape shape) {
        super.onStrokeWeightChanged(shape);
        refreshStyle();
    }

    private void _setMetrics(float azimuth, float fov, float extent) {
        _azimuth = azimuth;
        _fov = fov;
        _extent = extent;
        updatePolygon();
    }

    private void updatePolygon() {

        // in this case the camera point is not valid, so the polygon should not be computed
        if (Double.isNaN(_point.getLatitude())
                || Double.isNaN(_point.getLongitude()))
            return;

        LineString ls = new LineString(3);

        int numSlices = SLICES_PER_90 * (int) Math.ceil(_fov / 90);

        ls.addPoint(_point.getLongitude(), _point.getLatitude(), 0);

        for (int i = 0; i <= numSlices; i++) {
            GeoPoint gp = DistanceCalculations.metersFromAtBearing(
                    _point, _extent,
                    _azimuth - (_fov / 2.0f) + ((_fov / numSlices) * i));
            ls.addPoint(gp.getLongitude(), gp.getLatitude(), 0);

        }

        ls.addPoint(_point.getLongitude(), _point.getLatitude(), 0);

        _poly.setGeometry(new Polygon(ls));
    }
}
