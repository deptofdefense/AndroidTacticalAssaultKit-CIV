
package com.atakmap.android.maps.graphics;

import android.graphics.*;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.maps.*;
import com.atakmap.coremap.maps.coords.*;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.*;

import java.nio.*;

public class GLDogHouse extends AbstractGLMapItem2 implements
        Doghouse.DoghouseChangeListener {

    private static final float SEGMENT_SIZE = 120f;

    /** Increase this to widen the doghouses */
    private static final double NOSE_ANGLE = 120d; // degrees

    private Doghouse _doghouse;
    private final MapView _mapView;
    private FloatBuffer _vertices;
    private GLText _glText;
    private long _currentDraw;
    private boolean _recompute;
    private final Object _lock = new Object();
    private GeoPoint _midpoint;
    private GeoPoint _source;
    private Vector2D _offset;
    private double _bearing;
    private Doghouse.DoghouseLocation _routeSide;

    // TODO: this needs to directly extend AbstractGLMapItem2 in the future
    // TODO: I want to have width and height that dictate the shape of these
    public GLDogHouse(MapRenderer surface, Doghouse dh) {
        super(surface, dh, GLMapView.RENDER_PASS_SURFACE);
        _doghouse = dh;
        _mapView = MapView.getMapView();
        _currentDraw = 0L;
        _recompute = true;
        // allocate a buffer for 5 2D float vertices
        _vertices = Unsafe.allocateDirect(5 * 2 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        _glText = GLText.getInstance(
                MapView.getTextFormat(Typeface.DEFAULT,
                        _doghouse.getFontOffset()));
        bounds.set(-90, -180, 90, 180);

        _midpoint = GeoPoint.createMutable();
        _source = GeoPoint.createMutable();
        _offset = new Vector2D();
        _bearing = 0.0;
        _routeSide = Doghouse.DoghouseLocation.OUTSIDE_OF_TURN;
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        // check that the current render pass is GLMapView.RENDER_PASS_SURFACE
        if ((renderPass & this.renderPass) == 0) {
            // not a SURFACE render pass, move along
            return;
        }
        synchronized (_lock) {
            if (_doghouse == null
                    || ortho.drawMapResolution > (double) _doghouse
                            .getMaxVisibleScale()) {
                return;
            }
        }

        if (_currentDraw != ortho.drawVersion) {
            _recompute = true;
        }
        _currentDraw = ortho.drawVersion;

        if (_vertices == null) {
            return;
        }

        int rows;
        String[] data;
        double translation;
        double bearing;
        int strokeWidth;
        int strokeColor;
        float[] shadeColor;
        float[] textColor;
        GeoPoint midpoint;
        Doghouse.DoghouseLocation routeSide;
        synchronized (_lock) {
            rows = _doghouse.size();
            data = new String[rows];
            for (int i = 0; i < rows; i++) {
                data[i] = _doghouse.getData(i);
            }

            translation = _doghouse.getTotalTranslation();

            routeSide = _doghouse.getRelativeLocation();

            bearing = _doghouse.getBearing() - ortho.drawRotation;
            strokeWidth = _doghouse.getStrokeWidth();
            strokeColor = _doghouse.getStrokeColor();
            shadeColor = _doghouse.getShadeColor();
            textColor = _doghouse.getTextColor();
            _source.set(_doghouse.getSource());
            midpoint = GeoCalculations.midPoint(_doghouse.getSource(), _doghouse.getTarget());
        }

        PointF midpointF = ortho.forward(midpoint);
        if (!_midpoint.equals(midpoint) || _bearing != bearing || _routeSide != routeSide) {
            _midpoint.set(midpoint);
            _bearing = bearing;
            _routeSide = routeSide;

            // Calculate the offset of the doghouse by finding the normal of the line segment,
            // based on which side the doghouse should be on, and scale it by the translation value.
            _offset.x = _source.getLongitude() - midpoint.getLongitude();
            _offset.y = _source.getLatitude() - midpoint.getLatitude();
            _offset = _offset.normalize();
            if (_routeSide == Doghouse.DoghouseLocation.LEFT_OF_ROUTE) {
                double tmpY = _offset.y;
                _offset.y = -_offset.x;
                _offset.x = tmpY;
            } else {
                double tmpX = _offset.x;
                _offset.x = -_offset.y;
                _offset.y = tmpX;
            }
            _offset.x *= translation;
            _offset.y *= translation;

            // Calculate the point positions of the doghouse in model space
            PointF nose = new PointF(0.0f, 1.0f * SEGMENT_SIZE);

            PointF topRight = new PointF(1.0f * SEGMENT_SIZE, 0.5f * SEGMENT_SIZE);

            PointF topLeft = new PointF(-1.0f * SEGMENT_SIZE, 0.5f * SEGMENT_SIZE);

            PointF bottomRight = new PointF(1.0f * SEGMENT_SIZE, -0.5f * SEGMENT_SIZE * rows);

            PointF bottomLeft = new PointF(-1.0f * SEGMENT_SIZE, -0.5f * SEGMENT_SIZE * rows);

            GeoPoint[] geoPoints = new GeoPoint[] {
                    ortho.inverse(new PointF(nose.x + midpointF.x + (float)_offset.x, nose.y + midpointF.y + (float)_offset.y)),
                    ortho.inverse(new PointF(topLeft.x + midpointF.x + (float)_offset.x, topLeft.y + midpointF.y + (float)_offset.y)),
                    ortho.inverse(new PointF(bottomLeft.x + midpointF.x + (float)_offset.x, bottomLeft.y + midpointF.y + (float)_offset.y)),
                    ortho.inverse(new PointF(bottomRight.x + midpointF.x + (float)_offset.x, bottomRight.y + midpointF.y + (float)_offset.y)),
                    ortho.inverse(new PointF(topRight.x + midpointF.x + (float)_offset.x, topRight.y + midpointF.y + (float)_offset.y)),
            };
            computeGeoBounds(geoPoints);

            // pack the vertices in the buffer in counter-clockwise order
            _vertices.clear();
            _vertices.put(nose.x);
            _vertices.put(nose.y);
            _vertices.put(topLeft.x);
            _vertices.put(topLeft.y);
            _vertices.put(bottomLeft.x);
            _vertices.put(bottomLeft.y);
            _vertices.put(bottomRight.x);
            _vertices.put(bottomRight.y);
            _vertices.put(topRight.x);
            _vertices.put(topRight.y);
            _vertices.flip();

        }

        _recompute = false;

        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(
                GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glColor4f(
                shadeColor[1],
                shadeColor[2],
                shadeColor[3],
                shadeColor[0]);

        GLES20FixedPipeline.glPushMatrix();

        // translate the doghouse to the line segment midpoint plus the offset value we calculated.
        GLES20FixedPipeline.glTranslatef(
                midpointF.x + (float)_offset.x,
                midpointF.y + (float)_offset.y,
                0.0f);
        GLES20FixedPipeline.glRotatef(
                (float) bearing * -1f,
                0.0f,
                0.0f,
                1.0f);

        GLES20FixedPipeline.glVertexPointer(
                2, // number of coords per vertex
                GLES20FixedPipeline.GL_FLOAT,
                0,
                _vertices);

        GLES20FixedPipeline.glDrawArrays(
                GLES20FixedPipeline.GL_TRIANGLE_FAN,
                0,
                _vertices.limit() / 2);

        GLES20FixedPipeline.glLineWidth(strokeWidth);
        GLES20FixedPipeline.glColor4f(
                Color.red(strokeColor) / 255f,
                Color.green(strokeColor) / 255f,
                Color.blue(strokeColor) / 255f,
                Color.alpha(strokeColor) / 255f);
        GLES20FixedPipeline.glDrawArrays(
                GLES20FixedPipeline.GL_LINE_LOOP,
                0,
                _vertices.limit() / 2);
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        // shift the bottom left corner of the doghouse to the origin
        GLES20FixedPipeline.glTranslatef(
                _vertices.get(4), // bottom left x
                _vertices.get(5), // bottom left y
                0.0f);

        for (int line = 0; line < rows; line++) {
            String displayData = data[line];
            String glString = GLText.localize(displayData);
            float textWidth = _glText.getStringWidth(glString);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(
                    SEGMENT_SIZE - textWidth / 2.0f,
                    (SEGMENT_SIZE / 2) * (rows - line),
                    0.0f);
            _glText.draw(
                    glString,
                    textColor[1],
                    textColor[2],
                    textColor[3],
                    textColor[0]);
            GLES20FixedPipeline.glPopMatrix();
        }

        GLES20FixedPipeline.glPopMatrix();

    }

    @Override
    public void startObserving() {
        Log.d("GLDoghouse", "State: startObserving");
        super.startObserving();
        if (_doghouse != null) {
            _doghouse.registerDoghouseChangeListener(this);
        }
    }

    @Override
    public void stopObserving() {
        Log.d("GLDoghouse", "State: stopObserving");
        super.stopObserving();
        if (_doghouse != null) {
            _doghouse.unregisterDoghouseChangeListener(this);
        }
    }

    @Override
    public void release() {
        Log.d("GLDoghouse", "State: release");
        _vertices.clear();
        Unsafe.free(_vertices);
        _vertices = null;
        _glText = null;
    }

    @Override
    public void onDoghouseChanged(Doghouse doghouse) {
        synchronized (_lock) {
            _doghouse = doghouse;
//            _recompute = true;
        }
    }


    @Override
    public void onDoghouseRemoved(Doghouse doghouse) {
        synchronized (_lock) {
            _doghouse = null;
        }
    }

    public boolean isBatchable(GLMapView view) {
        return false;
    }

    private void computeGeoBounds(GeoPoint[] points) {
        double south = 90;
        double west = 180;
        double north = -90;
        double east = -180;
        if (points != null) {
            for (GeoPoint gp : points) {
                double lat = gp.getLatitude();
                double lon = gp.getLongitude();
                south = Math.min(lat, south);
                west = Math.min(lon, west);
                north = Math.max(lat, north);
                east = Math.max(lon, east);
            }
        }

        bounds.set(south, west, north, east);
        synchronized (_lock) {
            if (_doghouse != null && points != null) {
                _doghouse.setPoints(points);
            }
        }
        dispatchOnBoundsChanged();
    }
}
