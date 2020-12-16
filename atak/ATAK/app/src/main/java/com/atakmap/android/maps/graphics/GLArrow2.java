
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.atakmap.android.maps.Arrow;
import com.atakmap.android.maps.Arrow.OnTextChangedListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.Shape.OnPointsChangedListener;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import java.nio.FloatBuffer;

public class GLArrow2 extends GLShape2 implements OnPointsChangedListener,
        OnTextChangedListener {

    private final Arrow _subject;
    private float _textAngle;
    private float _textWidth;
    private float _textHeight;
    private final PointD _textPoint = new PointD(0d, 0d, 0d);
    private final FloatBuffer _arrowHead;

    private GLText _glText;
    private String _text;
    private String[] _textLines;
    private final float[] _textColor = new float[] {
            1.0f, 1.0f, 1.0f, 1.0f
    };
    private final GLNinePatch _ninePatch;
    private static final float div_2 = 1f / 2f;
    private static final double div_180_pi = 180d / Math.PI;
    private static final double div_pi_4 = Math.PI / 4f;

    private final static boolean CLAMP_TO_GROUND_ENABLED = true;
    private final static boolean XRAY_ENABLED = true;

    /**
     * The minimum distance that must be exceeded before clamping is enabled.
     * Lines less than this distance do not appear to shift with respect to
     * earth's curvature when compared to imagery without terrain loaded.
     * Distances of 40km or more are observed to have some noticeable shift.
     */
    private static final double minClampDistance = 30000d;
    private static final double slantMinElAngle = 10d;

    private GeoPoint[] _pts;
    private boolean drawText = true;
    private long currentDraw = 0;

    private boolean _clampToGround = false;

    private final GLBatchLineString impl;
    private final GLBatchLineString ximpl;

    public GLArrow2(MapRenderer surface, Arrow arrow) {
        super(surface,
                arrow,
                GLMapView.RENDER_PASS_SPRITES | GLMapView.RENDER_PASS_SURFACE);

        _subject = arrow;
        _arrowHead = Unsafe.allocateDirect(9, FloatBuffer.class);
        _ninePatch = GLRenderGlobals.get(surface).getMediumNinePatch();
        updateText(GLText.localize(arrow.getText()), arrow.getTextColor());
        this.impl = new GLBatchLineString(surface);
        this.ximpl = new GLBatchLineString(surface);
    }

    @Override
    public void startObserving() {
        super.startObserving();
        refreshStyle();
        this.onPointsChanged(_subject);
        _subject.addOnStrokeColorChangedListener(this);
        _subject.addOnPointsChangedListener(this);
        _subject.addOnTextChangedListener(this);
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        _subject.removeOnStrokeColorChangedListener(this);
        _subject.removeOnPointsChangedListener(this);
        _subject.removeOnTextChangedListener(this);
    }

    @Override
    public void onStrokeColorChanged(Shape subject) {
        super.onStrokeColorChanged(subject);
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
    public void onStrokeWeightChanged(Shape subject) {
        super.onStrokeWeightChanged(subject);
        if (context.isRenderThread())
            refreshStyle();
        else
            context.queueEvent(new Runnable() {
                public void run() {
                    refreshStyle();
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

    private void _buildArrow(GLMapView ortho) {

        if (currentDraw == ortho.drawVersion) {
            return;
        }
        currentDraw = ortho.drawVersion;

        // XXX - LOTS of room for improvement here!
        GeoPoint[] pts = _pts;
        int ptLen = pts.length;

        // not enough points supplied - do not attempt to draw
        if (ptLen < 2)
            return;

        float[] points = new float[ptLen * 3 + 9];

        float xmin = Float.MAX_VALUE;
        float xmax = Float.MIN_VALUE;
        float ymin = Float.MAX_VALUE;
        float ymax = Float.MIN_VALUE;

        this.bounds.setWrap180(ortho.continuousScrollEnabled);
        double unwrap = ortho.idlHelper.getUnwrap(this.bounds);
        for (int x = 0; x < pts.length; x++) {
            double terrain = 0d;
            if (ortho.drawTilt > 0d)
                terrain = ortho.getTerrainMeshElevation(pts[x].getLatitude(),
                        pts[x].getLongitude());
            forward(ortho, pts[x], ortho.scratch.pointD, unwrap, terrain,
                    false);
            float xpos = (float) ortho.scratch.pointD.x;
            float ypos = (float) ortho.scratch.pointD.y;
            float zpos = (float) ortho.scratch.pointD.z;
            points[3 * x] = xpos;
            points[3 * x + 1] = ypos;
            points[3 * x + 2] = zpos;
            if (xpos < xmin)
                xmin = xpos;
            if (xpos > xmax)
                xmax = xpos;
            if (ypos < ymin)
                ymin = ypos;
            if (ypos > ymax)
                ymax = ypos;
        }

        float screenRot = 0f;

        if (_glText == null) {
            _glText = GLText.getInstance(MapView.getTextFormat(
                    Typeface.DEFAULT, +2));
            updateText(_text, _subject.getTextColor());
        }

        if (xmax - xmin < _textWidth
                && ymax - ymin < _textWidth
                || _text.length() == 0) {
            drawText = false;
            _textPoint.x = (xmax + xmin) / 2;
            _textPoint.y = ymin - _textHeight / 2;
            _textAngle = 0;
        } else {
            drawText = true;
            if (ptLen % 2 == 0) {
                int idx = 3 * (ptLen / 2 - 1);

                PointD startPoint = new PointD(points[idx], points[idx + 1],
                        points[idx + 2]);
                PointD endPoint = new PointD(points[idx + 3], points[idx + 4],
                        points[idx + 5]);

                float xmid = (int) ((startPoint.x + endPoint.x) / 2);
                float ymid = (int) ((startPoint.y + endPoint.y) / 2);
                float zmax = (int) Math.min(startPoint.z, endPoint.z);

                // obtain the bounds of the current view
                RectF _view = this.getWidgetViewF();

                // find the point that is contained in the image, if both points are outside the 
                // image, it does not matter.

                if (startPoint.y < _view.top &&
                        startPoint.x < _view.right &&
                        startPoint.x > 0 &&
                        startPoint.y > 0) {
                    //Log.d("SHB", "start point is inside the view");
                } else {
                    //Log.d("SHB", "end point is inside the view");
                    PointD tmp = startPoint;
                    startPoint = endPoint;
                    endPoint = tmp;
                }

                // determine the intersection point, if both points intersect, center between them
                // if one point intersects, draw the text to be the midline between the point 
                // inside the view and the intersection point.

                PointF[] ip = GLMapItem._getIntersectionPoint(_view,
                        new PointF((float) startPoint.x,
                                (float) startPoint.y),
                        new PointF((float) endPoint.x, (float) endPoint.y));

                if (ip[0] != null || ip[1] != null) {
                    if (ip[0] != null && ip[1] != null) {
                        xmid = (ip[0].x + ip[1].x) / 2.0f;
                        ymid = (ip[0].y + ip[1].y) / 2.0f;
                    } else {

                        if (ip[0] != null) {
                            //Log.d("SHB", "bottom is clipped");
                            xmid = (float) (ip[0].x + startPoint.x) / 2.0f;
                            ymid = (float) (ip[0].y + startPoint.y) / 2.0f;
                        } else {
                            //Log.d("SHB", "top is clipped");
                            xmid = (float) (ip[1].x + startPoint.x) / 2.0f;
                            ymid = (float) (ip[1].y + startPoint.y) / 2.0f;
                        }
                    }
                }

                _textAngle = (float) (Math.atan2(points[idx + 1]
                        - points[idx + 4],
                        points[idx]
                                - points[idx + 3])
                        * div_180_pi)
                        - screenRot;

                _textPoint.x = xmid;
                _textPoint.y = ymid;
                _textPoint.z = zmax;
            } else {
                // XXX - indexing here is probably broken
                int idx = 3 * (ptLen - 1) / 2;
                float xmid = (int) points[idx];
                float ymid = (int) points[idx + 1];
                _textAngle = (float) (Math.atan2(points[idx - 2]
                        - points[idx + 4],
                        points[idx - 3]
                                - points[idx + 3])
                        * div_180_pi)
                        - screenRot;
                _textPoint.y = xmid;
                _textPoint.x = ymid;
                _textPoint.z = 0d;
            }
        }
        if (_textAngle > 90 || _textAngle < -90)
            _textAngle += 180;
        int zx = ptLen * 3 - 3, zy = ptLen * 3 - 2, zz = ptLen * 3 - 1;
        int yx = ptLen * 3 - 6, yy = ptLen * 3 - 5;

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
        // Could be in half or third display of dropdown, so use the offset;
        float right = ((GLMapView) this.context).focusx * 2;
        // Could be in portrait mode as well, so change the bottom accordingly
        float top = ((GLMapView) this.context).focusy * 2;
        return new RectF(0f, top - MapView.getMapView().getActionBarHeight(),
                right, 0);
    }

    private void _setColor(int color) {
        GLES20FixedPipeline.glColor4f(Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f, Color.alpha(color) / 255f);
    }

    @Override
    public void onPointsChanged(Shape s) {
        final GeoPoint[] p = s.getPoints();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _pts = p;

                final int numPts = _pts != null ? _pts.length : 0;

                double dist = 0d;
                double minEl = 0d;
                double maxEl = 0d;
                LineString ls = new LineString(3);
                for (int i = 0; i < numPts; i++) {
                    final double lat = _pts[i].getLatitude();
                    final double lng = _pts[i].getLongitude();
                    final double hae = !Double.isNaN(_pts[i].getAltitude())
                            ? _pts[i].getAltitude()
                            : 0d;
                    ls.addPoint(lng, lat, hae);

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

                // XXX - whether the R&B representation is slant or surface
                //       should really be deferred to the user. Any attempt to
                //       automatically determine which is appropriate is likely
                //       to miss the mark in some user interactions
                if (CLAMP_TO_GROUND_ENABLED) {
                    final double elANgle = Math
                            .toDegrees(Math.atan2(maxEl - minEl, dist));
                    _clampToGround = (dist > minClampDistance) && // distance less than 4km
                            elANgle < slantMinElAngle;
                }

                impl.setAltitudeMode(
                        _clampToGround ? Feature.AltitudeMode.ClampToGround
                                : Feature.AltitudeMode.Absolute);
                impl.setTessellationEnabled(_clampToGround);

                ximpl.setAltitudeMode(
                        _clampToGround ? Feature.AltitudeMode.ClampToGround
                                : Feature.AltitudeMode.Absolute);
                ximpl.setTessellationEnabled(_clampToGround);

                MapView mv = MapView.getMapView();
                bounds.set(_pts, mv.isContinuousScrollEnabled());
                dispatchOnBoundsChanged();
                currentDraw = 0;
            }
        });
    }

    @Override
    public void onTextChanged(Arrow arrow) {
        final String text = arrow.getText();
        final int textColor = arrow.getTextColor();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                updateText(GLText.localize(text), textColor);
                currentDraw = 0;
            }
        });
    }

    private void updateText(String text, int textColor) {
        _text = text;
        _textLines = _text.split("\n");
        if (_glText != null) {
            _textWidth = _glText.getStringWidth(_text);
            _textHeight = _glText.getStringHeight(_text);
        }
        _textColor[0] = Color.red(textColor) / 255f;
        _textColor[1] = Color.green(textColor) / 255f;
        _textColor[2] = Color.blue(textColor) / 255f;
        _textColor[3] = Color.alpha(textColor) / 255f;
    }

    /**
     * Expects GL_VERTEX_ARRAY client state to be enabled and _arrowHead
     * geometry uploaded as vertex pointer
     */
    private void _drawArrowHead() {
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

        if (!MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE)
                && _clampToGround)
            return;
        else if (renderPass == GLMapView.RENDER_PASS_SURFACE && !_clampToGround)
            return;

        if (_pts != null)
            _buildArrow(ortho);
        if (_pts != null && _glText != null) {

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glLoadIdentity();

            final boolean drawSprite = MathUtils.hasBits(renderPass,
                    GLMapView.RENDER_PASS_SPRITES);

            // draw the arrow line
            if (XRAY_ENABLED && drawSprite) {
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

            if (XRAY_ENABLED && drawSprite) {
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

            if (drawText) {
                GLES20FixedPipeline.glTranslatef((float) _textPoint.x,
                        (float) _textPoint.y,
                        0f);
                GLES20FixedPipeline.glRotatef(_textAngle, 0f, 0f, 1f);
                GLES20FixedPipeline.glTranslatef(-_textWidth / 2,
                        -_textHeight / 2 + _glText.getDescent(), 0);
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(-8f,
                        -_glText.getDescent() - 4f, 0f);
                GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.8f);
                if (_ninePatch != null)
                    _ninePatch.draw(_textWidth + 16f, _textHeight + 8f);
                GLES20FixedPipeline.glPopMatrix();
                for (int i = 0; i < _textLines.length; i++) {
                    GLES20FixedPipeline.glPushMatrix();
                    GLES20FixedPipeline.glTranslatef(0f,
                            ((_textLines.length - 1) - i)
                                    * _glText.getCharHeight(),
                            0f);
                    _glText.draw(_textLines[i], _textColor[0], _textColor[1],
                            _textColor[2], _textColor[3]);
                    GLES20FixedPipeline.glPopMatrix();
                }
            }
            GLES20FixedPipeline.glPopMatrix();
        }
    }

}
