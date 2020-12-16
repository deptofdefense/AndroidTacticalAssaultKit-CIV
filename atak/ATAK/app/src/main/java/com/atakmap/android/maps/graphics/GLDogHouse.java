
package com.atakmap.android.maps.graphics;

import android.graphics.*;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.maps.*;
import com.atakmap.coremap.maps.coords.*;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapBatchable;
import com.atakmap.map.opengl.GLMapSurface;
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
    private double sinBearing;
    private double cosBearing;
    private double sinRotate90;
    private double cosRotate90;
    private double sinMHalfNoseAngle;
    private double cosMHalfNoseAngle;
    private double sinPHalfNoseAngle;
    private double cosPHalfNoseAngle;
    private final float _textMidline;
    private FloatBuffer _vertices;
    private GLText _glText;
    private long _currentDraw;
    private boolean _recompute;
    private final Object _lock = new Object();

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

        // compute and cache the text midline point
        // a and b are sides of the nose triangle that are known
        // and in this case, they are equal
        // c is the side that is unknown and being computed
        // the nose angle is preset
        // Using: Law of Cosines
        // Original: cosC = (a^2 + b^2 - c^2) / 2ab
        // Rerranged: c^2 = a^2 + b^2 - 2abcosC
        // a = b, the triangle is isosceles
        // C = angle opposite side c, which we are computing = NOSE_ANGLE = 120
        // Becomes: c^2 = 2a^2 - 2a^2cos(120)
        double a = SEGMENT_SIZE;
        double c_squared = 2 * Math.pow(a, 2)
                - 2 * Math.pow(a, 2) * Math.cos(Math.toRadians(NOSE_ANGLE));
        double c = Math.sqrt(c_squared);
        _textMidline = (float) (c / 2.0d);
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
        synchronized (_lock) {
            rows = _doghouse.size();
            data = new String[rows];
            for (int i = 0; i < rows; i++) {
                data[i] = _doghouse.getData(i);
            }

            translation = _doghouse.getTotalTranslation();

            if (_doghouse
                    .getRelativeLocation() == Doghouse.DoghouseLocation.RIGHT_OF_ROUTE) {
                translation *= -1; // go the other way
            }

            bearing = _doghouse.getBearing() - ortho.drawRotation;
            strokeWidth = _doghouse.getStrokeWidth();
            strokeColor = _doghouse.getStrokeColor();
            shadeColor = _doghouse.getShadeColor();
            textColor = _doghouse.getTextColor();
        }

        if (_recompute) {
            setConversions(bearing);

            GeoPoint midpoint;
            synchronized (_lock) {
                midpoint = _doghouse.getNose();
            }
            PointF midpointF = ortho.forward(midpoint);

            int nX = (int) (midpointF.x + translation * sinRotate90);
            int nY = (int) (midpointF.y + translation * cosRotate90);
            PointF nose = new PointF(new Point(nX, nY));

            int tRX = (int) (nose.x - SEGMENT_SIZE * sinMHalfNoseAngle);
            int tRY = (int) (nose.y - SEGMENT_SIZE * cosMHalfNoseAngle);
            PointF topRight = new PointF(new Point(tRX, tRY));

            int tLX = (int) (nose.x - SEGMENT_SIZE * sinPHalfNoseAngle);
            int tLY = (int) (nose.y - SEGMENT_SIZE * cosPHalfNoseAngle);
            PointF topLeft = new PointF(new Point(tLX, tLY));

            int bRX = (int) (tRX - (SEGMENT_SIZE / 2) * rows * sinBearing);
            int bRY = (int) (tRY - (SEGMENT_SIZE / 2) * rows * cosBearing);
            PointF bottomRight = new PointF(new Point(bRX, bRY));

            int bLX = (int) (tLX - (SEGMENT_SIZE / 2) * rows * sinBearing);
            int bLY = (int) (tLY - (SEGMENT_SIZE / 2) * rows * cosBearing);
            PointF bottomLeft = new PointF(new Point(bLX, bLY));

            GeoPoint[] geoPoints = new GeoPoint[] {
                    ortho.inverse(nose),
                    ortho.inverse(topLeft),
                    ortho.inverse(bottomLeft),
                    ortho.inverse(bottomRight),
                    ortho.inverse(topRight),
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
        GLES20FixedPipeline.glPushMatrix();
        // shift the bottom left corner of the doghouse to the origin
        GLES20FixedPipeline.glTranslatef(
                _vertices.get(4), // bottom left x
                _vertices.get(5), // bottom left y
                0.0f);
        GLES20FixedPipeline.glRotatef(
                (float) bearing * -1f,
                0.0f,
                0.0f,
                1.0f);

        for (int line = 0; line < rows; line++) {
            String displayData = data[line];
            String glString = GLText.localize(displayData);
            float textWidth = _glText.getStringWidth(glString);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(
                    _textMidline - textWidth / 2.0f,
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
            _recompute = true;
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

    private void setConversions(double azimuth) {
        sinBearing = Math.sin(Math.toRadians(azimuth));
        cosBearing = Math.cos(Math.toRadians(azimuth));
        sinRotate90 = Math.sin(Math.toRadians(azimuth - 90));
        cosRotate90 = Math.cos(Math.toRadians(azimuth - 90));
        double halfNoseAngle = NOSE_ANGLE / 2;
        sinMHalfNoseAngle = Math.sin(Math.toRadians(azimuth - halfNoseAngle));
        cosMHalfNoseAngle = Math.cos(Math.toRadians(azimuth - halfNoseAngle));
        sinPHalfNoseAngle = Math.sin(Math.toRadians(azimuth + halfNoseAngle));
        cosPHalfNoseAngle = Math.cos(Math.toRadians(azimuth + halfNoseAngle));
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
