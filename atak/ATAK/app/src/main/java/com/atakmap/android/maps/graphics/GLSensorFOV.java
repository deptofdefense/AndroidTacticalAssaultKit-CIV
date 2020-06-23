
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.maps.SensorFOV.OnMetricsChangedListener;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.Shape.OnPointsChangedListener;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GLSensorFOV extends GLShape implements OnMetricsChangedListener,
        OnPointsChangedListener {

    private final static int NUM_SLICES = 8;

    GeoPoint _point;
    float _azimuth = 0f;
    float _fov = 10f;

    float _red = 1f;
    float _green = 1f;
    float _blue = 1f;
    float _alpha = 0.3f;

    float _extent;
    private FloatBuffer _verts;

    public GLSensorFOV(MapRenderer surface, SensorFOV subject) {
        super(surface, subject);
        _setMetrics(subject.getAzimuth(), subject.getFOV(),
                subject.getExtent(), subject.getColor());
        _point = subject.getPoint().get();
        float[] color = subject.getColor();
        if (color.length != 4) {
            _red = color[0];
            _green = color[1];
            _blue = color[2];
            _alpha = color[3];
        }
    }

    @Override
    public void draw(GLMapView ortho) {
        if (_verts == null) {
            _verts = com.atakmap.lang.Unsafe
                    .allocateDirect((2 + NUM_SLICES) * 4 * 2)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
        }

        // XXX - not the most efficient mechanism, but it effectively restricts
        //       the cone to the extent/FOV. at a mimium, the cone verts should
        //       probably be computed every time the point/metrics change to
        //       avoid intra-frame overhead. we could consider scaling the unit
        //       cone by the extent converted to pixels, however, resolutions
        //       reported are nominal and computed pixel extents would vary with
        //       latitude.

        int idx = 0;

        ortho.scratch.geo.set(_point);
        ortho.forward(ortho.scratch.geo, ortho.scratch.pointF);
        _verts.put(idx++, ortho.scratch.pointF.x);
        _verts.put(idx++, ortho.scratch.pointF.y);

        for (int i = 0; i <= NUM_SLICES; i++) {
            ortho.scratch.geo.set(DistanceCalculations.metersFromAtBearing(
                    _point, _extent,
                    _azimuth - (_fov / 2.0f) + ((_fov / NUM_SLICES) * i)));
            ortho.forward(ortho.scratch.geo, ortho.scratch.pointF);
            _verts.put(idx++, ortho.scratch.pointF.x);
            _verts.put(idx++, ortho.scratch.pointF.y);
        }

        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                _verts);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);

        GLES20FixedPipeline.glColor4f(_red, _green, _blue, _alpha);
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN, 0,
                        _verts.limit() / 2);

        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);

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
        final float[] color = fov.getColor();

        renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                _setMetrics(azimuth, f, extent, color);
            }
        });
    }

    @Override
    public void onPointsChanged(Shape fov) {
        final SensorFOV sfov = (SensorFOV) fov;
        final GeoPoint point = sfov.getPoint().get();
        renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                _point = point;
                // update the bounds and notify the listeners
                sfov.getBounds(bounds);
                OnBoundsChanged();
            }
        });
    }

    private void _setMetrics(float azimuth, float fov, float extent,
            float[] color) {
        _azimuth = azimuth;
        _fov = fov;
        _extent = extent;
        _red = color[0];
        _green = color[1];
        _blue = color[2];
        _alpha = color[3];
    }

}
