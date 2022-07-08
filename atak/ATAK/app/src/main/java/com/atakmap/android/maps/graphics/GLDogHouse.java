
package com.atakmap.android.maps.graphics;

import android.graphics.*;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.maps.*;
import com.atakmap.coremap.maps.coords.*;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.map.hittest.HitTestResult;
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
import com.atakmap.opengl.*;

import java.nio.*;

public class GLDogHouse extends AbstractGLMapItem2 implements
        Doghouse.DoghouseChangeListener {

    private final String TAG = "GLDogHouse";

    private static final float SEGMENT_SIZE = 120f;

    /** Increase this to widen the doghouses */
    private static final double NOSE_ANGLE = 120d; // degrees

    // These variables should only be used on the GL thread
    private Doghouse _doghouse;
    private FloatBuffer _vertices;
    private GLText _glText;
    private final GeoPoint _midpoint;
    private final GeoPoint _source;
    private final Vector2D _offset;
    private double _bearing;
    private Doghouse.DoghouseLocation _routeSide;
    boolean lastSurface = false;
    private LineString _surfacePoints;
    private Polygon _surfaceGeom;
    private GLBatchPolygon _surfaceRenderer;
    private Style _surfaceStyle;
    private GeoPoint _doghouseOriginGeo;
    private int _rows;

    private int _strokeColor;
    private int _shaderColor;
    private float _strokeWidth;
    private boolean _surfaceGeomDirty;
    private boolean _surfaceStyleDirty;
    private int _surfaceVersion = -1;

    // TODO: I want to have width and height that dictate the shape of these
    public GLDogHouse(MapRenderer surface, Doghouse dh) {
        super(surface, dh,
                GLMapView.RENDER_PASS_SPRITES | GLMapView.RENDER_PASS_SURFACE);
        _doghouse = dh;
        bounds.set(-90, -180, 90, 180);

        _midpoint = GeoPoint.createMutable();
        _source = GeoPoint.createMutable();
        _offset = new Vector2D();
        _bearing = 0.0;
        _routeSide = Doghouse.DoghouseLocation.OUTSIDE_OF_TURN;
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {

        final boolean surface = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SURFACE);
        final boolean sprites = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES);

        // XXX - temporary workaround to enable higher quality graphics with
        //       new surface rendering system -- render the doghouse during the
        //       sprites pass when the map is not tilted, during the surface
        //       pass when tilted. I believe long term solution is to always
        //       render these graphics as sprites, but this will require
        //       passing through clamp-to-ground information along with segment
        //       at altitude
        if ((surface && ortho.currentScene.drawTilt == 0d) ||
                (sprites && ortho.currentScene.drawTilt > 0d)) {

            if (surface && lastSurface) {
                SurfaceRendererControl ctrl = ortho
                        .getControl(SurfaceRendererControl.class);
                if (ctrl != null)
                    ctrl.markDirty(new Envelope(bounds.getWest(),
                            bounds.getSouth(), 0d, bounds.getEast(),
                            bounds.getNorth(), 0d), true);
                lastSurface = false;
            }
            return;
        }

        // check that the current render pass is valid for this renderable
        if ((renderPass & this.renderPass) == 0) {
            // not a SURFACE render pass, move along
            return;
        }

        if (_doghouse == null
                || ortho.currentScene.drawMapResolution > (double) _doghouse
                        .getMaxVisibleScale()) {
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

        rows = _doghouse.size();
        data = new String[rows];
        for (int i = 0; i < rows; i++) {
            data[i] = _doghouse.getData(i);
        }

        translation = _doghouse.getTotalTranslation();

        routeSide = _doghouse.getRelativeLocation();

        bearing = _doghouse.getBearing();
        strokeWidth = _doghouse.getStrokeWidth();
        strokeColor = _doghouse.getStrokeColor();
        shadeColor = _doghouse.getShadeColor();
        textColor = _doghouse.getTextColor();
        _source.set(_doghouse.getSource());
        midpoint = _doghouse.getNose();

        // validate geometry
        final boolean geometryDirty = (rows != _rows) ||
                (bearing != _bearing) ||
                !_midpoint.equals(midpoint) ||
                (routeSide != _routeSide);
        _rows = rows;
        _surfaceGeomDirty |= geometryDirty;
        _midpoint.set(midpoint);
        _bearing = bearing;
        _routeSide = routeSide;

        // validate style
        _surfaceStyleDirty |= (strokeWidth != _strokeWidth) ||
                (strokeColor != _strokeColor) ||
                (color(shadeColor[1], shadeColor[2], shadeColor[3],
                        shadeColor[0]) != _shaderColor);
        _strokeWidth = strokeWidth;
        _strokeColor = strokeColor;
        _shaderColor = color(shadeColor[1], shadeColor[2], shadeColor[3],
                shadeColor[0]);

        ortho.forward(midpoint, ortho.scratch.pointD);
        // check clip
        if (ortho.scratch.pointD.z >= 1d)
            return;

        float ssOriginX = 0f;
        float ssOriginY = 0f;
        float relativeScaleX = 1f;
        float relativeScaleY = 1f;

        if (sprites) {
            if (geometryDirty || _vertices == null) {
                // Calculate the offset of the doghouse by finding the normal of the line segment,
                // based on which side the doghouse should be on, and scale it by the translation value.
                double localBearing = bearing;
                if (ortho.drawSrid == 4326)
                    localBearing = Math.toDegrees(Math.atan2(
                            _source.getLatitude() - midpoint.getLatitude(),
                            _source.getLongitude() - midpoint.getLongitude()));
                if (_routeSide == Doghouse.DoghouseLocation.LEFT_OF_ROUTE) {
                    localBearing -= 90d;
                } else {
                    localBearing += 90d;
                }
                _offset.x = translation * Math
                        .sin(Math.toRadians(
                                localBearing - ortho.currentPass.drawRotation));
                _offset.y = translation * Math
                        .cos(Math.toRadians(
                                localBearing - ortho.currentPass.drawRotation));

                // Calculate the point positions of the doghouse in model space
                PointF nose = new PointF(0.0f, 1.0f * SEGMENT_SIZE);
                PointF topRight = new PointF(1.0f * SEGMENT_SIZE,
                        0.5f * SEGMENT_SIZE);
                PointF topLeft = new PointF(-1.0f * SEGMENT_SIZE,
                        0.5f * SEGMENT_SIZE);
                PointF bottomRight = new PointF(1.0f * SEGMENT_SIZE,
                        -0.5f * SEGMENT_SIZE * rows);
                PointF bottomLeft = new PointF(-1.0f * SEGMENT_SIZE,
                        -0.5f * SEGMENT_SIZE * rows);

                PointF midpointF = ortho.scratch.pointF;
                midpointF.x = (float) ortho.scratch.pointD.x;
                midpointF.y = (float) ortho.scratch.pointD.y;
                GeoPoint[] geoPoints = new GeoPoint[] {
                        ortho.inverse(new PointF(
                                nose.x + midpointF.x + (float) _offset.x,
                                nose.y + midpointF.y + (float) _offset.y)),
                        ortho.inverse(new PointF(
                                topLeft.x + midpointF.x + (float) _offset.x,
                                topLeft.y + midpointF.y + (float) _offset.y)),
                        ortho.inverse(new PointF(
                                bottomLeft.x + midpointF.x + (float) _offset.x,
                                bottomLeft.y + midpointF.y
                                        + (float) _offset.y)),
                        ortho.inverse(new PointF(
                                bottomRight.x + midpointF.x + (float) _offset.x,
                                bottomRight.y + midpointF.y
                                        + (float) _offset.y)),
                        ortho.inverse(new PointF(
                                topRight.x + midpointF.x + (float) _offset.x,
                                topRight.y + midpointF.y + (float) _offset.y)),
                };
                computeGeoBounds(geoPoints);

                // allocate a buffer for 5 2D float vertices
                if (_vertices == null)
                    _vertices = Unsafe.allocateDirect(5 * 2, FloatBuffer.class);

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

            final float bearingOffset = (_routeSide == Doghouse.DoghouseLocation.LEFT_OF_ROUTE)
                    ? -90f
                    : 90f;

            relativeScaleX = 1f;
            relativeScaleY = 1f;

            ssOriginX = (float) ortho.scratch.pointD.x + (float) (Math
                    .sin(Math.toRadians(
                            bearing + bearingOffset
                                    - ortho.currentPass.drawRotation))
                    * translation);
            ssOriginY = (float) ortho.scratch.pointD.y + (float) (Math
                    .cos(Math.toRadians(
                            bearing + bearingOffset
                                    - ortho.currentPass.drawRotation))
                    * translation);

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
            GLES20FixedPipeline.glTranslatef(ssOriginX, ssOriginY, 0f);
            GLES20FixedPipeline.glRotatef(
                    (float) (bearing - ortho.currentPass.drawRotation) * -1f,
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

            GLES20FixedPipeline.glLineWidth(
                    strokeWidth / ortho.currentPass.relativeScaleHint);
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

            GLES20FixedPipeline.glPopMatrix();
        }
        if (surface) {
            if (_surfaceVersion != ortho.currentScene.drawVersion) {
                _surfaceGeomDirty = true;
                _surfaceStyleDirty = true;
                _surfaceVersion = ortho.currentScene.drawVersion;
            }

            if (_surfaceRenderer == null) {
                _surfaceRenderer = new GLBatchPolygon(ortho);
                _surfacePoints = new LineString(2);
                _surfacePoints.addPoints(new double[12], 0, 6, 2);
                _surfaceGeom = new Polygon(_surfacePoints);
                _surfaceGeomDirty = true;
                _surfaceStyleDirty = true;
            }
            if (_surfaceGeomDirty) {
                final double xtd = ortho.currentScene.drawMapResolution
                        * translation;
                final double xt_crs = _bearing
                        + (_routeSide == Doghouse.DoghouseLocation.LEFT_OF_ROUTE
                                ? -1d
                                : 1d) * 90d;

                _doghouseOriginGeo = GeoCalculations.pointAtDistance(midpoint,
                        xt_crs, xtd);
                GeoPoint xt_mid = _doghouseOriginGeo;
                GeoPoint xt_nose = GeoCalculations.pointAtDistance(xt_mid,
                        _bearing, 1.0d * SEGMENT_SIZE
                                * ortho.currentScene.drawMapResolution);
                GeoPoint xt_top = GeoCalculations.pointAtDistance(xt_mid,
                        _bearing, 0.5d * SEGMENT_SIZE
                                * ortho.currentScene.drawMapResolution);
                GeoPoint xt_top_left = GeoCalculations.pointAtDistance(xt_top,
                        _bearing - 90d, 1.0d * SEGMENT_SIZE
                                * ortho.currentScene.drawMapResolution);
                GeoPoint xt_top_right = GeoCalculations.pointAtDistance(xt_top,
                        _bearing + 90d, 1.0d * SEGMENT_SIZE
                                * ortho.currentScene.drawMapResolution);
                GeoPoint xt_bottom = GeoCalculations.pointAtDistance(xt_mid,
                        _bearing + 180d, 0.5d * SEGMENT_SIZE * rows
                                * ortho.currentScene.drawMapResolution);
                GeoPoint xt_bottom_left = GeoCalculations.pointAtDistance(
                        xt_bottom, _bearing - 90d, 1.0d * SEGMENT_SIZE
                                * ortho.currentScene.drawMapResolution);
                GeoPoint xt_bottom_right = GeoCalculations.pointAtDistance(
                        xt_bottom, _bearing + 90d, 1.0d * SEGMENT_SIZE
                                * ortho.currentScene.drawMapResolution);

                _surfacePoints.setX(0, xt_nose.getLongitude());
                _surfacePoints.setY(0, xt_nose.getLatitude());
                _surfacePoints.setX(1, xt_top_right.getLongitude());
                _surfacePoints.setY(1, xt_top_right.getLatitude());
                _surfacePoints.setX(2, xt_bottom_right.getLongitude());
                _surfacePoints.setY(2, xt_bottom_right.getLatitude());
                _surfacePoints.setX(3, xt_bottom_left.getLongitude());
                _surfacePoints.setY(3, xt_bottom_left.getLatitude());
                _surfacePoints.setX(4, xt_top_left.getLongitude());
                _surfacePoints.setY(4, xt_top_left.getLatitude());
                _surfacePoints.setX(5, xt_nose.getLongitude());
                _surfacePoints.setY(5, xt_nose.getLatitude());

                computeGeoBounds(new GeoPoint[] {
                        xt_nose,
                        xt_top_right,
                        xt_bottom_right,
                        xt_bottom_left,
                        xt_top_left,
                });

                _surfaceRenderer.setGeometry(_surfaceGeom);
                _surfaceGeomDirty = false;
            }
            if (_surfaceStyleDirty) {
                _surfaceStyle = new CompositeStyle(new Style[] {
                        new BasicFillStyle(color(shadeColor[1], shadeColor[2],
                                shadeColor[3], shadeColor[0])),
                        new BasicStrokeStyle(strokeColor, strokeWidth),
                });

                _surfaceRenderer.setStyle(_surfaceStyle);
                _surfaceStyleDirty = false;
            }
            lastSurface = true;
            _surfaceRenderer.draw(ortho);

            // compute x and y scale
            final float ssv = (float) orthoScreenSpaceDistance(ortho,
                    _surfacePoints.getY(4), _surfacePoints.getX(4),
                    _surfacePoints.getY(3), _surfacePoints.getX(3));

            // measure horizontal distance between top points
            final float ssh = (float) orthoScreenSpaceDistance(ortho,
                    _surfacePoints.getY(4), _surfacePoints.getX(4),
                    _surfacePoints.getY(1), _surfacePoints.getX(1));

            relativeScaleX = ssh / (2 * SEGMENT_SIZE);
            relativeScaleY = ssv / (SEGMENT_SIZE * (rows - 1));

            ortho.scene.forward(_doghouseOriginGeo, ortho.scratch.pointD);
            ssOriginX = (float) ortho.scratch.pointD.x;
            ssOriginY = (float) ortho.scratch.pointD.y;
        }

        if (sprites || surface)
            drawText(ortho,
                    data,
                    rows,
                    bearing - ortho.currentPass.drawRotation,
                    ssOriginX, ssOriginY,
                    relativeScaleX, relativeScaleY,
                    textColor[1], textColor[2], textColor[3], textColor[0]);
    }

    private void drawText(GLMapView ortho, String[] data, int rows,
            double bearing, float tx, float ty, float scaleX, float scaleY,
            float r, float g, float b, float a) {

        if (_doghouse == null)
            return;

        if (_glText == null) {
            _glText = GLText.getInstance(
                    MapView.getTextFormat(Typeface.DEFAULT,
                            _doghouse.getFontOffset()));
        }

        final PointF bottomLeft = new PointF(-1.0f * SEGMENT_SIZE,
                -0.5f * SEGMENT_SIZE * rows);

        GLES20FixedPipeline.glPushMatrix();

        // translate the doghouse to the line segment midpoint plus the offset value we calculated.
        GLES20FixedPipeline.glTranslatef(tx, ty, 0f);

        GLES20FixedPipeline.glRotatef(
                (float) bearing * -1f,
                0.0f,
                0.0f,
                1.0f);

        GLES20FixedPipeline.glScalef(scaleX, scaleY, 1f);

        // shift the bottom left corner of the doghouse to the origin
        GLES20FixedPipeline.glTranslatef(
                bottomLeft.x, // bottom left x
                bottomLeft.y, // bottom left y
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

            _glText.draw(glString, r, g, b, a);
            GLES20FixedPipeline.glPopMatrix();
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void startObserving() {
        Log.d(TAG, "State: startObserving");
        super.startObserving();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (_doghouse != null)
                    _doghouse.registerDoghouseChangeListener(GLDogHouse.this);
            }
        });
    }

    @Override
    public void stopObserving() {
        Log.d(TAG, "State: stopObserving");
        super.stopObserving();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (_doghouse != null)
                    _doghouse.unregisterDoghouseChangeListener(GLDogHouse.this);
            }
        });
    }

    @Override
    public void release() {
        Log.d(TAG, "State: release");
        if (_vertices != null) {
            _vertices.clear();
            Unsafe.free(_vertices);
            _vertices = null;
        }
        _glText = null;
        if (_surfaceRenderer != null) {
            _surfaceRenderer.release();
            _surfaceRenderer = null;
        }
        _surfaceGeom = null;
        _surfaceStyle = null;
        _surfacePoints = null;
        _surfaceVersion = -1;
        _surfaceGeomDirty = true;
        _surfaceStyleDirty = true;
    }

    @Override
    public void onDoghouseChanged(final Doghouse doghouse) {
        setDoghouse(doghouse);
    }

    @Override
    public void onDoghouseRemoved(Doghouse doghouse) {
        setDoghouse(null);
    }

    private void setDoghouse(final Doghouse dh) {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (_doghouse != null)
                    _doghouse.unregisterDoghouseChangeListener(GLDogHouse.this);
                _doghouse = dh;
                if (_doghouse != null)
                    _doghouse.registerDoghouseChangeListener(GLDogHouse.this);
            }
        });
    }

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        return bounds.intersects(params.bounds)
                ? new HitTestResult(subject, params.geo)
                : null;
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

        if (_doghouse != null && points != null) {
            _doghouse.setPoints(points);
        }
        dispatchOnBoundsChanged();
    }

    private static int color(float r, float g, float b, float a) {
        return (((int) (a * 255f)) << 24) |
                (((int) (r * 255f)) << 16) |
                (((int) (g * 255f)) << 8) |
                ((int) (b * 255f));
    }

    private static double orthoScreenSpaceDistance(GLMapView ortho, double lat0,
            double lng0, double lat1, double lng1) {
        // unwrap
        if (Math.abs(lng1 - lng0) > 180d) {
            if (lng0 < 0d)
                lng1 -= 360d;
            else
                lng1 += 360d;
        }

        ortho.scratch.geo.set(lat0, lng0);
        ortho.scene.forward(ortho.scratch.geo, ortho.scratch.pointF);
        final float x0 = ortho.scratch.pointF.x;
        final float y0 = ortho.scratch.pointF.y;

        ortho.scratch.geo.set(lat1, lng1);
        ortho.scene.forward(ortho.scratch.geo, ortho.scratch.pointF);
        final float x1 = ortho.scratch.pointF.x;
        final float y1 = ortho.scratch.pointF.y;

        return MathUtils.distance(x0, y0, x1, y1);
    }
}
