
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.atakmap.android.maps.Arrow;
import com.atakmap.android.maps.Arrow.OnTextChangedListener;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.Shape.OnPointsChangedListener;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.Vector3D;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLLabelManager;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Plane;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;

import java.nio.FloatBuffer;
import java.util.ConcurrentModificationException;

public class GLArrow2 extends GLShape2 implements OnPointsChangedListener,
        OnTextChangedListener, MapItem.OnAltitudeModeChangedListener {

    private final Arrow _subject;
    protected final FloatBuffer _arrowHead;

    protected int _labelID = GLLabelManager.NO_ID;
    protected String _text;
    protected static final double div_pi_4 = Math.PI / 4f;

    protected final static boolean XRAY_ENABLED = true;

    /**
     * The minimum distance that must be exceeded before clamping is enabled.
     * Lines less than this distance do not appear to shift with respect to
     * earth's curvature when compared to imagery without terrain loaded.
     * Distances of 40km or more are observed to have some noticeable shift.
     */
    private static final double minClampDistance = 30000d;
    private static final double slantMinElAngle = 10d;
    private static final double threshold = 10000;

    protected GeoPoint[] _pts;

    private boolean _forceClamp = false;
    private boolean _nadirClamp = false;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected boolean _clampToGround = false;
    private Envelope _geomBounds;

    private final GLBatchLineString impl;
    private final GLBatchLineString ximpl;
    protected final GLLabelManager _labelManager;

    private boolean _ptsAgl;
    private int _terrainVersion;
    protected int _arrowheadVersion;

    public GLArrow2(MapRenderer surface, Arrow arrow) {
        super(surface,
                arrow,
                GLMapView.RENDER_PASS_SPRITES | GLMapView.RENDER_PASS_SURFACE);

        _labelManager = ((GLMapView) surface).getLabelManager();

        _subject = arrow;
        _arrowHead = Unsafe.allocateDirect(9, FloatBuffer.class);
        updateText(GLText.localize(arrow.getText()), arrow.getTextColor());
        this.impl = new GLBatchLineString(surface);
        this.impl.setTesselationThreshold(threshold);
        this.ximpl = new GLBatchLineString(surface);
        this.ximpl.setTesselationThreshold(threshold);

        _terrainVersion = -1;
        _arrowheadVersion = -1;
    }

    @Override
    public void startObserving() {
        super.startObserving();
        _subject.addOnStrokeColorChangedListener(this);
        _subject.addOnPointsChangedListener(this);
        _subject.addOnTextChangedListener(this);
        _subject.addOnAltitudeModeChangedListener(this);
        _subject.addOnStyleChangedListener(this);

        refreshStyle();
        this.onPointsChanged(_subject);
        this.onAltitudeModeChanged(subject.getAltitudeMode());
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        removeLabel();
        _subject.removeOnStrokeColorChangedListener(this);
        _subject.removeOnPointsChangedListener(this);
        _subject.removeOnTextChangedListener(this);
        _subject.removeOnAltitudeModeChangedListener(this);
        _subject.removeOnStyleChangedListener(this);
    }

    private void safeRefreshStyle() {
        if (context.isRenderThread())
            refreshStyle();
        else
            context.queueEvent(new Runnable() {
                public void run() {
                    refreshStyle();
                }
            });

    }

    @Override
    public void onStyleChanged(Shape shape) {
        super.onStyleChanged(shape);
        safeRefreshStyle();
    }

    @Override
    public void onStrokeColorChanged(Shape subject) {
        super.onStrokeColorChanged(subject);
        safeRefreshStyle();
    }

    @Override
    public void onStrokeWeightChanged(Shape subject) {
        super.onStrokeWeightChanged(subject);
        safeRefreshStyle();
    }

    @Override
    public void onAltitudeModeChanged(Feature.AltitudeMode altitudeMode) {
        ximpl.setAltitudeMode(altitudeMode);
        impl.setAltitudeMode(altitudeMode);
        if (_labelID != GLLabelManager.NO_ID) {
            _labelManager.setAltitudeMode(_labelID, altitudeMode);
        }
        _clampToGround = altitudeMode
                .equals(Feature.AltitudeMode.ClampToGround);

    }

    protected boolean ensureLabel() {
        if (_labelID == GLLabelManager.NO_ID) {
            _labelID = _labelManager.addLabel();
            MapTextFormat mapTextFormat = MapView
                    .getTextFormat(Typeface.DEFAULT, +2);
            _labelManager.setTextFormat(_labelID, mapTextFormat);
            _labelManager.setFill(_labelID, true);
            _labelManager.setHints(_labelID, GLLabelManager.HINT_XRAY);

            _labelManager.setBackgroundColor(_labelID,
                    Color.argb(204/*=80%*/, 0, 0, 0));
            _labelManager.setVerticalAlignment(_labelID,
                    GLLabelManager.VerticalAlignment.Middle);
            _labelManager.setVisible(_labelID, this.visible);
            updateText(_text, _subject.getTextColor());
            return true;
        }
        return false;
    }

    private void removeLabel() {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (_labelID != GLLabelManager.NO_ID) {
                    _labelManager.removeLabel(_labelID);
                    _labelID = GLLabelManager.NO_ID;
                }
            }
        });
    }

    private void refreshStyle() {
        final int xraymask = 0x1FFFFFFF;

        BasicStrokeStyle stroke = new BasicStrokeStyle(this.strokeColor,
                this.strokeWeight);
        BasicStrokeStyle outline = new BasicStrokeStyle(
                this.strokeColor & 0xFF000000, this.strokeWeight + 2f);
        this.impl.setStyle(new CompositeStyle(new Style[] {
                outline, stroke
        }));
        BasicStrokeStyle xstroke = new BasicStrokeStyle(
                this.strokeColor & xraymask, this.strokeWeight);
        BasicStrokeStyle xoutline = new BasicStrokeStyle(xraymask,
                this.strokeWeight + 2f);
        this.ximpl.setStyle(new CompositeStyle(new Style[] {
                xoutline, xstroke
        }));
    }

    protected void _validateArrowhead(GLMapView ortho) {
        if (_pts.length < 2)
            return;

        _arrowheadVersion = ortho.currentPass.drawVersion;

        // Fetch last 2 points w/ altitude
        GeoPoint[] pts = new GeoPoint[2];
        float[] points = new float[6];
        for (int i = 0; i < 2; i++) {
            ortho.scratch.geo.set(_pts[_pts.length - (2 - i)]);

            // Note: interpreting NAN HAE as 0AGL
            final boolean ptAgl = (_pts[i]
                    .getAltitudeReference() == GeoPoint.AltitudeReference.AGL ||
                    Double.isNaN(_pts[i].getAltitude()));
            if (ptAgl)
                ortho.scratch.geo.set(getHae(ortho, ortho.scratch.geo));

            pts[i] = new GeoPoint(ortho.scratch.geo);
        }

        // Compute the nearby tail end of the arrow for more accurate forward results
        double distance = pts[1].distanceTo(pts[0]);
        double bearing = pts[1].bearingTo(pts[0]);
        double inclination = Math.toDegrees(Math
                .atan2(pts[0].getAltitude() - pts[1].getAltitude(), distance));
        pts[0] = GeoCalculations.pointAtDistance(pts[1], bearing,
                ortho.currentScene.drawMapResolution, inclination);

        // Get the tail and head in screen coordinates so we can calculate the on-screen angle
        for (int i = 0; i < 2; i++) {
            ortho.forward(pts[i], ortho.scratch.pointD);
            points[i * 3] = (float) ortho.scratch.pointD.x;
            points[i * 3 + 1] = (float) ortho.scratch.pointD.y;
            points[i * 3 + 2] = (float) ortho.scratch.pointD.z;
        }

        int zx = 3, zy = 4, zz = 5;
        int yx = 0, yy = 1;

        // unused commenting out for now
        //int ax = ptLen * 3, ay = ptLen * 3 + 1, az = ptLen * 3 + 2;
        //int bx = ptLen * 3 + 3, by = ptLen * 3 + 4, bz = ptLen * 3 + 5;
        //int cx = ptLen * 3 + 6, cy = ptLen * 3 + 7, cz = ptLen * 3 + 8;

        double ang = Math.atan2(points[yy] - points[zy], points[yx]
                - points[zx]);
        double ang_plus_pi_div_4 = ang + div_pi_4;
        double ang_minus_pi_div_4 = ang - div_pi_4;
        _arrowHead.clear();
        _arrowHead.put(points[zx] + (float) Math.cos(ang_plus_pi_div_4) * 16);
        _arrowHead.put(points[zy] + (float) Math.sin(ang_plus_pi_div_4) * 16);
        _arrowHead.put(points[zz]);
        _arrowHead.put(points[zx]);
        _arrowHead.put(points[zy]);
        _arrowHead.put(points[zz]);
        _arrowHead.put(points[zx] + (float) Math.cos(ang_minus_pi_div_4) * 16);
        _arrowHead.put(points[zy] + (float) Math.sin(ang_minus_pi_div_4) * 16);
        _arrowHead.put(points[zz]);
        _arrowHead.flip();
    }

    /**
      * Retrieve the bounding RectF of the current state of the Map. This accounts for the
      * OrthoMapView's focus, so DropDowns will be accounted for.
      *
      * @return
      * @return The bounding RectF
      */
    protected RectF getWidgetViewF() {
        return getWidgetViewF((GLMapView) this.context);
    }

    static RectF getWidgetViewF(GLMapView ortho) {
        // Could be in half or third display of dropdown, so use the offset;
        float right = ortho.currentScene.right;
        // Could be in portrait mode as well, so change the bottom accordingly
        float top = ortho.currentScene.top;
        return new RectF(ortho.currentScene.left, top, right,
                ortho.currentScene.bottom);
    }

    protected void _setColor(int color) {
        GLES20FixedPipeline.glColor4f(Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f, Color.alpha(color) / 255f);
    }

    @Override
    public void onPointsChanged(Shape s) {
        final GeoPoint[] p = s.getPoints();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                pointsChangedImpl((GLMapView) context, p, true);
            }
        });
    }

    protected double getHae(GLMapView ortho, GeoPoint geo) {
        final boolean ptAgl = (geo
                .getAltitudeReference() == GeoPoint.AltitudeReference.AGL ||
                Double.isNaN(geo.getAltitude()));
        if (!ptAgl)
            return geo.getAltitude();

        double hae = geo.getAltitude();
        if (Double.isNaN(hae))
            hae = 0d;
        final double terrain = ortho.getTerrainMeshElevation(geo.getLatitude(),
                geo.getLongitude());
        if (!Double.isNaN(terrain))
            hae += terrain;
        return hae;
    }

    private void pointsChangedImpl(GLMapView ortho, GeoPoint[] p,
            boolean updateBounds) {
        _pts = p;
        _ptsAgl = false;

        final int numPts = _pts != null ? _pts.length : 0;

        GeoPoint[] labelPoints = new GeoPoint[numPts];

        double dist = 0d;
        double minEl = 0d;
        double maxEl = 0d;
        LineString ls = new LineString(3);
        for (int i = 0; i < numPts; i++) {
            // Note: interpreting NAN HAE as 0AGL
            final boolean ptAgl = (_pts[i]
                    .getAltitudeReference() == GeoPoint.AltitudeReference.AGL ||
                    Double.isNaN(_pts[i].getAltitude()));
            _ptsAgl |= ptAgl;

            final double lat = _pts[i].getLatitude();
            final double lng = _pts[i].getLongitude();
            final double hae = getHae(ortho, _pts[i]);
            ls.addPoint(lng, lat, hae);

            if (ptAgl)
                labelPoints[i] = new GeoPoint(lat, lng, hae);
            else
                labelPoints[i] = _pts[i];

            if (i > 0) {
                final double d = _pts[i].distanceTo(_pts[i - 1]);
                if (d > dist)
                    dist = d;
                if (hae < minEl)
                    minEl = hae;
                else if (hae > maxEl)
                    maxEl = hae;
            } else {
                minEl = hae;
                maxEl = hae;
            }
        }
        impl.setGeometry(ls);
        ximpl.setGeometry(ls);

        if (_labelID != GLLabelManager.NO_ID) {
            _labelManager.setGeometry(_labelID, ls);
        }

        _terrainVersion = ortho.getTerrainVersion();
        // invalidate arrowhead
        _arrowheadVersion = -1;

        if (updateBounds) {
            MapView mv = MapView.getMapView();
            if (mv != null) {
                _geomBounds = impl.getBounds(mv.getProjection()
                        .getSpatialReferenceID());
                if (_geomBounds != null) {
                    bounds.setWrap180(mv.isContinuousScrollEnabled());
                    bounds.set(_geomBounds.minY, _geomBounds.minX,
                            _geomBounds.maxY, _geomBounds.maxX);
                    bounds.setMinAltitude(_ptsAgl ? DEFAULT_MIN_ALT : minEl);
                    bounds.setMaxAltitude(_ptsAgl ? DEFAULT_MAX_ALT : maxEl);
                    dispatchOnBoundsChanged();
                }
            }
        }
    }

    @Override
    public void onTextChanged(Arrow arrow) {
        final String text = arrow.getText();
        final int textColor = arrow.getTextColor();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                updateText(GLText.localize(text), textColor);

            }
        });
    }

    @Override
    public void release() {
        super.release();
        if (_labelID != GLLabelManager.NO_ID) {
            _labelManager.removeLabel(_labelID);
            _labelID = GLLabelManager.NO_ID;
        }
        impl.release();
        ximpl.release();

        // force points re-validate on next draw
        _ptsAgl = true;
        _terrainVersion = -1;
    }

    protected void updateText(String text, int textColor) {
        _text = text;
        if (_labelID != 0) {
            _labelManager.setText(_labelID, _text);
            _labelManager.setColor(_labelID, textColor);
        }
    }

    /**
     * Expects GL_VERTEX_ARRAY client state to be enabled and _arrowHead
     * geometry uploaded as vertex pointer
     */
    protected void _drawArrowHead() {
        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 1f);
        GLES20FixedPipeline.glLineWidth(strokeWeight + 2);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_STRIP, 0,
                _arrowHead.limit() / 3);

        _setColor(strokeColor);
        GLES20FixedPipeline.glLineWidth(strokeWeight);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_STRIP, 0,
                _arrowHead.limit() / 3);
        GLES20FixedPipeline.glLineWidth(1f);
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if ((renderPass & this.renderPass) == 0)
            return;

        if (_pts == null)
            return;

        final boolean sprites = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES);
        final boolean surface = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SURFACE);

        boolean nadirClamp = surface ? _nadirClamp
                : getClampToGroundAtNadir()
                        && Double.compare(ortho.currentPass.drawTilt, 0) == 0;

        if (_ptsAgl && ortho.getTerrainVersion() != _terrainVersion ||
                _labelID == GLLabelManager.NO_ID
                || _nadirClamp != nadirClamp) {

            _nadirClamp = nadirClamp;

            // validate label if necessary
            ensureLabel();

            pointsChangedImpl(ortho, _pts, false);
        }

        final boolean renderGeom = (surface && _clampToGround)
                || (sprites && !_clampToGround);

        if (renderGeom && _pts != null) {
            if (_arrowheadVersion != ortho.currentPass.drawVersion)
                _validateArrowhead(ortho);

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glLoadIdentity();

            // draw the arrow line
            if (XRAY_ENABLED && sprites) {
                // if just doing the sprite pass, render the xray. disable
                // depth test and draw the geometry. we do not want to use
                // GL_ALWAYS as this will overwrite the depth buffer with
                // the 'z' for the line, which is not what we want
                GLES20FixedPipeline
                        .glDisable(GLES20FixedPipeline.GL_DEPTH_TEST);
                ximpl.draw(ortho);
                GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_DEPTH_TEST);
            }
            impl.draw(ortho);

            // draw the arrow head
            GLES20FixedPipeline
                    .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT,
                    0, _arrowHead);

            if (XRAY_ENABLED && sprites) {
                // if just doing the sprite pass, render the xray. disable
                // depth test and draw the geometry. we do not want to use
                // GL_ALWAYS as this will overwrite the depth buffer with
                // the 'z' for the line, which is not what we want
                GLES20FixedPipeline
                        .glDisable(GLES20FixedPipeline.GL_DEPTH_TEST);
                _drawArrowHead();
                GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_DEPTH_TEST);
            }

            _drawArrowHead();

            GLES20FixedPipeline
                    .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

            GLES20FixedPipeline.glPopMatrix();
        }
    }

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        HitTestResult result = impl.hitTest(renderer, params);
        return result != null ? new HitTestResult(_subject, result) : null;
    }

    /**
     * Given a line of bearing defined by B-A, finds the closest point on that
     * LOB to the focus point of the specified scene.
     * @param ortho
     * @param a
     * @param b
     * @return
     */
    static GeoPoint closestSurfacePointToFocus(GLMapView ortho, float xmid,
            float ymid, GeoPoint a, GeoPoint b) {
        ortho.scene.mapProjection.inverse(ortho.scene.camera.target,
                ortho.scratch.geo);
        if (Double.isNaN(ortho.scratch.geo.getAltitude()))
            ortho.scratch.geo.set(100d);
        else
            ortho.scratch.geo.set(ortho.scratch.geo.getAltitude() + 100d);
        ortho.scene.mapProjection.forward(ortho.scratch.geo,
                ortho.scratch.pointD);

        // compute focus as location as screen point at x,y on the plane
        // passing through the camera focus with the local up as the normal
        // vector
        com.atakmap.math.Vector3D normal = new com.atakmap.math.Vector3D(
                (ortho.scratch.pointD.x - ortho.scene.camera.target.x),
                (ortho.scratch.pointD.y - ortho.scene.camera.target.y),
                (ortho.scratch.pointD.z - ortho.scene.camera.target.z));
        Plane focusPlane = new Plane(normal, ortho.scene.camera.target);
        GeoPoint focus = GeoPoint.createMutable();
        if (ortho.scene.inverse(new PointF(xmid, ymid), focus,
                focusPlane) == null)
            ortho.scene.mapProjection.inverse(ortho.scene.camera.target, focus);

        if (ortho.drawSrid == 4978) {
            // compute the interpolation weight for the surface point
            ortho.scratch.geo.set(focus);
            final double dpts = GreatCircle.distance(a, b);
            double dtofocus = GreatCircle.alongTrackDistance(a, b,
                    ortho.scratch.geo);
            if (dtofocus < dpts &&
                    GreatCircle.distance(ortho.scratch.geo, a) < GreatCircle
                            .distance(ortho.scratch.geo,
                                    GeoCalculations.pointAtDistance(a, b,
                                            dtofocus / dpts))) {
                dtofocus *= -1d;
            }

            final double weight = MathUtils.clamp(dtofocus / dpts, 0d, 1d);

            return GeoCalculations.pointAtDistance(a, b,
                    weight);
        } else {
            // execute closest-point-on-line as cartesian math
            ortho.scene.mapProjection.forward(a, ortho.scratch.pointD);
            final double ax = ortho.scratch.pointD.x
                    * ortho.scene.displayModel.projectionXToNominalMeters;
            final double ay = ortho.scratch.pointD.y
                    * ortho.scene.displayModel.projectionYToNominalMeters;
            final double az = ortho.scratch.pointD.z
                    * ortho.scene.displayModel.projectionZToNominalMeters;
            ortho.scene.mapProjection.forward(b, ortho.scratch.pointD);
            final double bx = ortho.scratch.pointD.x
                    * ortho.scene.displayModel.projectionXToNominalMeters;
            final double by = ortho.scratch.pointD.y
                    * ortho.scene.displayModel.projectionYToNominalMeters;
            final double bz = ortho.scratch.pointD.z
                    * ortho.scene.displayModel.projectionZToNominalMeters;

            ortho.scene.mapProjection.forward(focus, ortho.scratch.pointD);
            final double dx = ortho.scratch.pointD.x
                    * ortho.scene.displayModel.projectionXToNominalMeters;
            final double dy = ortho.scratch.pointD.y
                    * ortho.scene.displayModel.projectionYToNominalMeters;
            final double dz = ortho.scratch.pointD.z
                    * ortho.scene.displayModel.projectionZToNominalMeters;

            double[] p = Vector3D.nearestPointOnSegment(dx, dy, dz, ax, ay, az,
                    bx, by, bz);

            ortho.scratch.pointD.x = p[0]
                    / ortho.scene.displayModel.projectionXToNominalMeters;
            ortho.scratch.pointD.y = p[1]
                    / ortho.scene.displayModel.projectionYToNominalMeters;
            ortho.scratch.pointD.z = p[2]
                    / ortho.scene.displayModel.projectionZToNominalMeters;

            return ortho.scene.mapProjection.inverse(ortho.scratch.pointD,
                    null);
        }
    }

    //https://edwilliams.org/avform.htm
    final static class GreatCircle {
        static double distance(GeoPoint a, GeoPoint b) {
            final double lat1 = Math.toRadians(a.getLatitude());
            final double lon1 = Math.toRadians(a.getLongitude());
            final double lat2 = Math.toRadians(b.getLatitude());
            final double lon2 = Math.toRadians(b.getLongitude());
            final double sin_dlat = (Math.sin((lat1 - lat2) / 2));
            final double sin_dlon = (Math.sin((lon1 - lon2) / 2));
            return 2 * Math.asin(Math.sqrt(sin_dlat * sin_dlat +
                    Math.cos(lat1) * Math.cos(lat2) * sin_dlon * sin_dlon));
        }

        static double course(GeoPoint a, GeoPoint b) {
            final double lat1 = Math.toRadians(a.getLatitude());
            final double lon1 = Math.toRadians(a.getLongitude());
            final double lat2 = Math.toRadians(b.getLatitude());
            final double lon2 = Math.toRadians(b.getLongitude());
            final double tc1 = Math.atan2(
                    Math.sin(lon1 - lon2) * Math.cos(lat2),
                    Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                            * Math.cos(lat2) * Math.cos(lon1 - lon2));
            if (tc1 > (Math.PI * 2d))
                return tc1 - Math.PI * 2d;
            else if (tc1 < (Math.PI * 2D))
                return tc1 + Math.PI * 2d;
            else
                return tc1;
        }

        static double alongTrackDistance(GeoPoint sp, GeoPoint ep, GeoPoint p) {
            final double crs_AB = course(sp, ep);
            final double crs_AD = course(sp, p);
            final double dist_AD = distance(sp, p);
            final double XTD = Math
                    .asin(Math.sin(dist_AD) * Math.sin(crs_AD - crs_AB));
            return Math.acos(Math.cos(dist_AD) / Math.cos(XTD));
        }
    }
}
