
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.Typeface;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.maps.SensorFOV.OnMetricsChangedListener;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.Shape.OnPointsChangedListener;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchPolygon;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLLabelManager;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to draw a {@link SensorFOV} cone on the map
 */
public class GLSensorFOV extends GLShape2 implements OnMetricsChangedListener,
        OnPointsChangedListener {

    private static final String TAG = "GLSensorFoV";
    private final static int SLICES_PER_90 = 8;

    //default and min spacing for range lines
    private final static int RANGE_LINES_PERIOD_M = 100;
    private final static int RANGE_LINES_MIN_M = 50;

    GeoPoint _point;
    float _azimuth = 0f;
    float _fov = 10f;

    boolean _labels = false;
    private String _labelL = null;
    private String _labelR = null;
    protected int _labelIDl = GLLabelManager.NO_ID;
    protected int _labelIDr = GLLabelManager.NO_ID;
    protected final GLLabelManager _labelManager;
    protected boolean _clampLabelToGround = true;
    protected int _textColor = Color.WHITE;

    private final GLMapView glMapView;
    private GLBatchLineString[] rangeLines;
    private float _rangeLinesSpacing = 10f;

    float _extent;
    private final GLBatchPolygon _poly;
    private SurfaceRendererControl _surfaceControl;

    public GLSensorFOV(MapRenderer surface, SensorFOV subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE);
        _labelManager = ((GLMapView) surface).getLabelManager();
        glMapView = (GLMapView) surface;

        _poly = new GLBatchPolygon(surface);
        _point = subject.getPoint().get();
        _setMetrics(subject.getAzimuth(), subject.getFOV(),
                subject.getExtent(), subject.isShowLabels(),
                subject.getLabelL(), subject.getLabelR(),
                subject.getRangeLines());
        refreshStyle();
    }

    /**
     * Ensures that the labels exist
     * @return true if the labels did not exist and were created.
     */
    protected boolean ensureLabel() {
        if (_labelIDl == GLLabelManager.NO_ID) {
            //Log.d("GLSensorFOV", "ensureLabel");
            _labelIDl = _labelManager.addLabel();
            MapTextFormat mapTextFormat = MapView
                    .getTextFormat(Typeface.DEFAULT, +2);
            _labelManager.setTextFormat(_labelIDl, mapTextFormat);
            _labelManager.setFill(_labelIDl, true);
            _labelManager.setHints(_labelIDl, GLLabelManager.HINT_XRAY);
            _labelManager.setBackgroundColor(_labelIDl,
                    Color.argb(204/*=80%*/, 0, 0, 0));
            _labelManager.setVerticalAlignment(_labelIDl,
                    GLLabelManager.VerticalAlignment.Middle);

            _labelIDr = _labelManager.addLabel();
            _labelManager.setTextFormat(_labelIDr, mapTextFormat);
            _labelManager.setFill(_labelIDr, true);
            _labelManager.setHints(_labelIDr, GLLabelManager.HINT_XRAY);
            _labelManager.setBackgroundColor(_labelIDr,
                    Color.argb(204/*=80%*/, 0, 0, 0));
            _labelManager.setVerticalAlignment(_labelIDr,
                    GLLabelManager.VerticalAlignment.Middle);
            return true;
        }
        return false;
    }

    private void removeLabel() {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (_labelIDl != GLLabelManager.NO_ID) {
                    _labelManager.removeLabel(_labelIDl);
                    _labelIDl = GLLabelManager.NO_ID;
                }

                if (_labelIDr != GLLabelManager.NO_ID) {
                    _labelManager.removeLabel(_labelIDr);
                    _labelIDr = GLLabelManager.NO_ID;
                }
            }
        });
    }

    private Style getStyle() {
        boolean stroke = strokeWeight > 0;
        final List<Style> composite = new ArrayList<>();
        composite.add(new BasicFillStyle(fillColor));
        if (stroke)
            composite.add(
                    new BasicStrokeStyle(strokeColor, strokeWeight));
        return new CompositeStyle(composite.toArray(new Style[0]));
    }

    private void refreshStyle() {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                final Style s = getStyle();

                _poly.setStyle(s);

                if (rangeLines != null) {
                    for (final GLBatchLineString l : rangeLines) {
                        if (l != null)
                            l.setStyle(s);
                    }
                }

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

        if(ensureLabel()) {
            // if the labels did not previously exist, go ahead and update the
            // polygon to reassociated them with the sensor field of view.
            updatePolygon();
        }

        _poly.draw(ortho);

        if (rangeLines == null)
            computeRangeLines();

        //render range lines
        if (rangeLines != null) {
            for (final GLBatchLineString l : rangeLines) {
                if (l != null)
                    l.draw(ortho);
            }
        }

    }

    private void computeRangeLines() {
        if (rangeLines != null) {
            for (GLBatchLineString l : rangeLines) {
                if (l != null)
                    l.release();
            }
        }

        if (isDrawLabels()) {
            float period = _rangeLinesSpacing;
            int num = (int) Math.floor(_extent / period);
            this.rangeLines = new GLBatchLineString[num];
            final Style s = getStyle();
            for (int i = 0; i < num; i++) {
                rangeLines[i] = constructRangeLine(glMapView, period * (i + 1));
                rangeLines[i].setStyle(s);
            }
        } else {
            rangeLines = new GLBatchLineString[0];
        }
    }

    private GLBatchLineString constructRangeLine(GLMapView view,
            double distance) {
        GLBatchLineString retval = new GLBatchLineString(view);
        LineString ls = new LineString(3);
        int numSlices = SLICES_PER_90 * (int) Math.ceil(_fov / 90);
        for (int i = 0; i <= numSlices; i++) {
            final GeoPoint gp = GeoCalculations.pointAtDistance(_point,
                    _azimuth - (_fov / 2.0f) + ((_fov / numSlices) * i),
                    distance, 0.0d);
            ls.addPoint(gp.getLongitude(), gp.getLatitude(), 0);
        }
        retval.setGeometry(ls);
        return retval;
    }

    @Override
    public void release() {

        if (rangeLines != null) {
            for (GLBatchLineString l : rangeLines)
                if (l != null)
                    l.release();
            rangeLines = null;
        }

        removeLabel();
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
        removeLabel();
        sensor.removeOnPointsChangedListener(this);
        sensor.removeOnMetricsChangedListener(this);
    }

    @Override
    public void onMetricsChanged(SensorFOV fov) {
        final float azimuth = fov.getAzimuth();
        final float f = fov.getFOV();
        final float extent = fov.getExtent();
        final boolean labels = fov.isShowLabels();
        final String labelL = fov.getLabelL();
        final String labelR = fov.getLabelR();
        final float rangeLines = fov.getRangeLines();
        //Log.d("GLSensorFOV", "onMetricsChanged: " + labels);

        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                _setMetrics(azimuth, f, extent, labels, labelL, labelR,
                        rangeLines);
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

    private boolean isDrawLabels() {
        return _labels && !FileSystemUtils.isEmpty(_labelL)
                && !FileSystemUtils.isEmpty(_labelR);
    }

    private void _setMetrics(float azimuth, float fov, float extent,
            boolean labels,
            String labelL, String labelR, float rangeLines) {
        //Log.d("GLSensorFOV", "_setMetrics: " + labels);

        _azimuth = azimuth;
        _fov = fov;
        _extent = extent;
        _labels = labels;
        _labelL = labelL;
        _labelR = labelR;
        _rangeLinesSpacing = rangeLines;
        if (_rangeLinesSpacing > _extent)
            _rangeLinesSpacing = RANGE_LINES_PERIOD_M;
        if (_rangeLinesSpacing < RANGE_LINES_MIN_M)
            _rangeLinesSpacing = RANGE_LINES_MIN_M;
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
            final GeoPoint gp = GeoCalculations.pointAtDistance(_point,
                    _azimuth - (_fov / 2.0f) + ((_fov / numSlices) * i),
                    _extent, 0.0d);
            ls.addPoint(gp.getLongitude(), gp.getLatitude(), 0);

        }

        ls.addPoint(_point.getLongitude(), _point.getLatitude(), 0);

        _poly.setGeometry(new Polygon(ls));

        ensureLabel();
        if (isDrawLabels() && this.subject.getVisible()
                && _labelIDl != GLLabelManager.NO_ID) {
            LineString lsLabels = new LineString(3);
            lsLabels.addPoint(_point.getLongitude(), _point.getLatitude(), 0);
            final GeoPoint end = GeoCalculations.pointAtDistance(_point,
                    _azimuth - (_fov / 2.0f), _extent, 0.0d);

            lsLabels.addPoint(end.getLongitude(), end.getLatitude(), 0);

            _labelManager.setGeometry(_labelIDl, lsLabels);
            _labelManager.setAltitudeMode(_labelIDl,
                    _clampLabelToGround ? Feature.AltitudeMode.ClampToGround
                            : Feature.AltitudeMode.Absolute);

            _labelManager.setText(_labelIDl, _labelL);
            _labelManager.setColor(_labelIDl, _textColor);
            _labelManager.setVisible(_labelIDl, true);
        } else {
            _labelManager.setVisible(_labelIDl, false);
        }

        if (isDrawLabels() && this.subject.getVisible()
                && _labelIDr != GLLabelManager.NO_ID) {
            LineString lsLabels = new LineString(3);
            lsLabels.addPoint(_point.getLongitude(), _point.getLatitude(), 0);
            final GeoPoint end = GeoCalculations.pointAtDistance(_point,
                    _azimuth + (_fov / 2.0f), _extent, 0.0d);
            lsLabels.addPoint(end.getLongitude(), end.getLatitude(), 0);

            _labelManager.setGeometry(_labelIDr, lsLabels);
            _labelManager.setAltitudeMode(_labelIDr,
                    _clampLabelToGround ? Feature.AltitudeMode.ClampToGround
                            : Feature.AltitudeMode.Absolute);

            _labelManager.setText(_labelIDr, _labelR);
            _labelManager.setColor(_labelIDr, _textColor);
            _labelManager.setVisible(_labelIDr, true);
        } else {
            _labelManager.setVisible(_labelIDr, false);
        }

        computeRangeLines();
    }

}
