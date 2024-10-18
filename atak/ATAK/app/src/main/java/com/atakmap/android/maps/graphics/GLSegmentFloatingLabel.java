
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.annotations.IncubatingApi;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Plane;
import com.atakmap.math.PointD;
import com.atakmap.math.Ray;
import com.atakmap.math.Rectangle;
import com.atakmap.math.Vector3D;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import java.util.List;

/** @deprecated DO NOT USE, interim code consolidation pending label manager */
@IncubatingApi(since = "4.3")
@Deprecated
@DeprecatedApi(since = "4.3", removeAt = "4.8", forRemoval = true)
public final class GLSegmentFloatingLabel {
    final static String TAG = "GLSegmentFloatingLabel";

    GeoPoint[] _pts = new GeoPoint[0];
    boolean _clampToGround = false;
    int _version = -1;
    PointD _textPoint = new PointD(0d, 0d, 0d);
    String _text;
    String[] _textLines;
    float _textWidth = 0f;
    float _textHeight = 0f;
    float _textAngle = 0f;
    boolean _drawText = false;
    GLText _glText;
    MutableGeoBounds _bounds = new MutableGeoBounds(0d, 0d, 0d, 0d);
    float _textColorR = 1f;
    float _textColorG = 1f;
    float _textColorB = 1f;
    float _textColorA = 1f;
    float _textBackgroundR = 0f;
    float _textBackgroundG = 0f;
    float _textBackgroundB = 0f;
    float _textBackgroundA = 0.8f;
    GLNinePatch _ninePatch;
    float _segmentPositionWeight = 0.5f;
    boolean _rotateAlign = true;

    float _insetLeft = 0f;
    float _insetRight = 0f;
    float _insetBottom = 0f;
    float _insetTop = 0f;

    public void update(GLMapView ortho) {
        if (ortho.currentPass.drawVersion == _version)
            return;
        _version = ortho.currentPass.drawVersion;

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

        // the near plane
        Plane near = null;

        _bounds.setWrap180(ortho.continuousScrollEnabled);
        double unwrap = ortho.idlHelper.getUnwrap(_bounds);
        for (int x = 0; x < pts.length; x++) {
            double terrain = 0d;
            // query for local terrain if it may be used
            if (_clampToGround || pts[x]
                    .getAltitudeReference() == GeoPoint.AltitudeReference.AGL) {
                terrain = ortho.getTerrainMeshElevation(pts[x].getLatitude(),
                        pts[x].getLongitude());
            }
            AbstractGLMapItem2.forward(ortho, pts[x], ortho.scratch.pointD,
                    unwrap, terrain,
                    false);
            float xpos = (float) ortho.scratch.pointD.x;
            float ypos = (float) ortho.scratch.pointD.y;
            float zpos = (float) ortho.scratch.pointD.z;

            // XXX - there may be two segments associated with each vertex,
            //       meaning that there will be potentially two intersection
            //       points with the near plane. The use-cases this code is
            //       currently employed against are single-line geometries,
            //       and this code is OBE with label manager. The code below
            //       addresses for the prevalent use-case and defers a more
            //       comprehensive fix to the label manager implementation.

            // points on the other side of the near plane are reflected and
            // won't end up at the appropriate viewport position. Attempt to
            // intersect the associated segment with the near clipping to
            // determine location.
            if (!_clampToGround && zpos > 1f) {
                final int x0 = x;
                final int x1 = MathUtils.clamp(
                        x0 + ((x == pts.length - 1) ? -1 : 1), 0,
                        pts.length - 1);
                if (x0 != x1) {
                    // find the intersect of the segment with the near plane
                    final double losdx = ortho.scene.camera.location.x
                            - ortho.scene.camera.target.x;
                    final double losdy = ortho.scene.camera.location.y
                            - ortho.scene.camera.target.y;
                    final double losdz = ortho.scene.camera.location.z
                            - ortho.scene.camera.target.z;

                    if (near == null)
                        near = new Plane(new Vector3D(losdx, losdy, losdz),
                                new PointD(ortho.scene.camera.location.x
                                        - (losdx / MathUtils.distance(losdx,
                                                losdy, losdz, 0d, 0d, 0d))
                                                * ortho.scene.camera.nearMeters,
                                        ortho.scene.camera.location.y - (losdy
                                                / MathUtils.distance(losdx,
                                                        losdy, losdz, 0d, 0d,
                                                        0d))
                                                * ortho.scene.camera.nearMeters,
                                        ortho.scene.camera.location.z - (losdz
                                                / MathUtils.distance(losdx,
                                                        losdy, losdz, 0d, 0d,
                                                        0d))
                                                * ortho.scene.camera.nearMeters));

                    ortho.scene.mapProjection.forward(pts[x0],
                            ortho.scratch.pointD);
                    final double p0x = ortho.scratch.pointD.x;
                    final double p0y = ortho.scratch.pointD.y;
                    final double p0z = ortho.scratch.pointD.z;
                    ortho.scene.mapProjection.forward(pts[x1],
                            ortho.scratch.pointD);
                    final double p1x = ortho.scratch.pointD.x;
                    final double p1y = ortho.scratch.pointD.y;
                    final double p1z = ortho.scratch.pointD.z;

                    // create segment
                    final PointD isect = near.intersect(new Ray(
                            new PointD(p0x, p0y, p0z),
                            new Vector3D(p1x - p0x, p1y - p0y, p1z - p0z)));
                    if (isect != null) {
                        ortho.scene.forward.transform(isect, isect);
                        xpos = (float) isect.x;
                        ypos = (float) isect.y;
                        zpos = (float) isect.z;
                    }
                }
            }
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
        if (near != null)
            near.dispose();

        float screenRot = 0f;

        if (_glText == null) {
            _glText = GLText.getInstance(MapView.getTextFormat(
                    Typeface.DEFAULT, +2));
            _validateText();
        }

        if (xmax - xmin < _textWidth
                && ymax - ymin < _textWidth
                || _text.length() == 0) {
            _drawText = false;
            _textPoint.x = (xmax + xmin) * _segmentPositionWeight;
            _textPoint.y = ymin - _textHeight / 2;
            _textAngle = 0;
        } else {
            _drawText = true;
            if (ptLen % 2 == 0) {
                int idx = 3 * (ptLen / 2 - 1);

                PointD startPoint = new PointD(points[idx], points[idx + 1],
                        points[idx + 2]);
                PointD endPoint = new PointD(points[idx + 3], points[idx + 4],
                        points[idx + 5]);

                final double d = MathUtils.distance(startPoint.x, startPoint.y,
                        endPoint.x, endPoint.y);
                final double dx = (endPoint.x - startPoint.x) / d;
                final double dy = (endPoint.y - startPoint.y) / d;

                float xmid = (float) (startPoint.x
                        + (dx * d * _segmentPositionWeight));
                float ymid = (float) (startPoint.y
                        + (dy * d * _segmentPositionWeight));
                float zmax = (float) Math.min(startPoint.z, endPoint.z);

                // obtain the bounds of the current view
                RectF view = GLArrow2.getWidgetViewF(ortho);

                int ptIdxStart = pts.length - 2;
                int ptIdxEnd = pts.length - 1;

                // determine the intersection point, if both points intersect, center between them
                // if one point intersects, draw the text to be the midline between the point
                // inside the view and the intersection point.

                final boolean containsSP = Rectangle.contains(view.left,
                        view.bottom, view.right, view.top, startPoint.x,
                        startPoint.y);
                final boolean containsEP = Rectangle.contains(view.left,
                        view.bottom, view.right, view.top, endPoint.x,
                        endPoint.y);
                if (!containsSP || !containsEP) {

                    Vector2D[] viewPoly = {
                            new Vector2D(view.left, view.top),
                            new Vector2D(view.right, view.top),
                            new Vector2D(view.right, view.bottom),
                            new Vector2D(view.left, view.bottom),
                            new Vector2D(view.left, view.top)
                    };
                    List<Vector2D> ip = Vector2D
                            .segmentIntersectionsWithPolygon(
                                    new Vector2D(startPoint.x, startPoint.y),
                                    new Vector2D(endPoint.x, endPoint.y),
                                    viewPoly);

                    if (!ip.isEmpty()) {
                        // fill in any missing endpoints

                        if (ip.size() == 1) {
                            if (containsSP)
                                ip.add(new Vector2D(startPoint.x,
                                        startPoint.y));
                            else
                                ip.add(new Vector2D(endPoint.x, endPoint.y));
                        }

                        // compute midpoint of intersecting segment
                        Vector2D p1 = ip.get(0);
                        Vector2D p2 = ip.get(1);

                        if (MathUtils.distance(p1.x, p1.y, startPoint.x,
                                startPoint.y) > MathUtils.distance(p2.x, p2.y,
                                        startPoint.x, startPoint.y)) {
                            Vector2D swap = p1;
                            p1 = p2;
                            p2 = swap;
                        }

                        double ipd = MathUtils.distance(p1.x, p1.y, p2.x, p2.y);
                        double ipdx = (p2.x - p1.x) / ipd;
                        double ipdy = (p2.y - p1.y) / ipd;

                        xmid = (float) (p1.x
                                + (ipdx * ipd * _segmentPositionWeight));
                        ymid = (float) (p1.y
                                + (ipdy * ipd * _segmentPositionWeight));
                    }
                }

                // apply some padding
                if (Rectangle.contains(view.left, view.bottom, view.right,
                        view.top, xmid, ymid)) {
                    float pad = 16f;
                    float padx = (_textWidth / 2f) + pad;
                    float pady = (_textHeight / 2f) + pad;
                    if (xmid - padx < view.left)
                        xmid = padx;
                    else if (xmid + padx > view.right)
                        xmid = view.right - padx;
                    if (ymid - pady < view.bottom)
                        ymid = pady;
                    else if (ymid + pady > view.top)
                        ymid = view.top - pady;
                }

                if (_clampToGround) {
                    GeoPoint textLoc = GeoPoint.createMutable();
                    textLoc.set(GLArrow2.closestSurfacePointToFocus(ortho, xmid,
                            ymid, pts[ptIdxStart], pts[ptIdxEnd]));

                    // use terrain mesh elevation
                    textLoc.set(ortho.getTerrainMeshElevation(
                            textLoc.getLatitude(), textLoc.getLongitude()));

                    final double weight = pts[ptIdxStart].distanceTo(textLoc) /
                            pts[ptIdxStart].distanceTo(pts[ptIdxEnd]);

                    ortho.forward(textLoc, ortho.scratch.pointD);

                    _textPoint.x = (float) ortho.scratch.pointD.x;
                    _textPoint.y = (float) ortho.scratch.pointD.y;
                    _textPoint.z = (float) ortho.scratch.pointD.z;

                    final double startBrg = textLoc.bearingTo(pts[ptIdxStart]);
                    final double endBrg = textLoc.bearingTo(pts[ptIdxEnd]);

                    // recompute `points` as small segment within screen bounds
                    // based on surface text location to ensure proper label
                    // rotation
                    ortho.scratch.geo.set(GeoCalculations.pointAtDistance(
                            textLoc, weight > 0d ? startBrg : -endBrg,
                            _textWidth / 2d * ortho.scene.gsd));
                    ortho.scratch.geo.set(ortho.getTerrainMeshElevation(
                            ortho.scratch.geo.getLatitude(),
                            ortho.scratch.geo.getLongitude()));
                    ortho.forward(ortho.scratch.geo, ortho.scratch.pointD);
                    points[0] = (float) ortho.scratch.pointD.x;
                    points[1] = (float) ortho.scratch.pointD.y;
                    points[2] = (float) ortho.scratch.pointD.z;
                    ortho.scratch.geo.set(GeoCalculations.pointAtDistance(
                            textLoc, weight < 1d ? endBrg : -startBrg,
                            _textWidth / 2d * ortho.scene.gsd));
                    ortho.scratch.geo.set(ortho.getTerrainMeshElevation(
                            ortho.scratch.geo.getLatitude(),
                            ortho.scratch.geo.getLongitude()));
                    ortho.forward(ortho.scratch.geo, ortho.scratch.pointD);
                    points[3] = (float) ortho.scratch.pointD.x;
                    points[4] = (float) ortho.scratch.pointD.y;
                    points[5] = (float) ortho.scratch.pointD.z;

                    // XXX - move a little closer to the camera also???

                    // select the closest depth amongst the label center and
                    // (approximate) endpoints
                    _textPoint.z = MathUtils.min(_textPoint.z, points[2],
                            points[5]);

                    // raise the label vertically above the surface as the
                    // tilt increases. use sin^2 to temper adjustment velocity
                    // during tilt
                    _textPoint.y += getSurfaceLabelOffset(ortho, _textHeight);
                } else {
                    _textPoint.x = xmid;
                    _textPoint.y = ymid;
                    _textPoint.z = zmax;
                }

                _textAngle = (float) (Math.atan2(points[idx + 1]
                        - points[idx + 4],
                        points[idx]
                                - points[idx + 3])
                        * 180d / Math.PI)
                        - screenRot;
            } else {
                // XXX - indexing here is probably broken
                int idx = 3 * (ptLen - 1) / 2;
                float xmid = (int) points[idx];
                float ymid = (int) points[idx + 1];
                _textAngle = (float) (Math.atan2(points[idx - 2]
                        - points[idx + 4],
                        points[idx - 3]
                                - points[idx + 3])
                        * 180d / Math.PI)
                        - screenRot;
                _textPoint.y = xmid;
                _textPoint.x = ymid;
                _textPoint.z = 0d;
            }
        }
        if (_textAngle > 90 || _textAngle < -90)
            _textAngle += 180;
    }

    public void getTextPoint(PointD point) {
        point.x = _textPoint.x;
        point.y = _textPoint.y;
        point.z = _textPoint.z;
    }

    public void setRotateToAlign(boolean b) {
        _rotateAlign = b;
        _version = -1;
    }

    public void setSegmentPositionWeight(float weight) {
        _segmentPositionWeight = weight;
        _version = -1;
    }

    public void setSegment(GeoPoint[] pts) {
        if (pts == null) {
            _pts = new GeoPoint[0];
        } else {
            _pts = new GeoPoint[pts.length];
            for (int i = 0; i < pts.length; i++)
                _pts[i] = new GeoPoint(pts[i]);
            _bounds.set(_pts);
        }
        _version = -1;
    }

    public void setClampToGround(boolean clampToGround) {
        _clampToGround = clampToGround;
        _version = -1;
    }

    public void setText(String text) {
        _text = text;
        _validateText();
        _version = -1;
    }

    public void setTextColor(int argb) {
        setTextColor(Color.red(argb) / 255f, Color.green(argb) / 255f,
                Color.blue(argb) / 255f, Color.alpha(argb) / 255f);
    }

    public void setTextColor(float r, float g, float b, float a) {
        _textColorR = r;
        _textColorG = g;
        _textColorB = b;
        _textColorA = a;
    }

    public void setTextFormat(Typeface face, int offset) {
        setTextFormat(MapView.getTextFormat(
                face, offset));
    }

    public void setTextFormat(MapTextFormat format) {
        _glText = GLText.getInstance(format);
        _validateText();
        _version = -1;
    }

    void setBackgroundColor(int argb) {
        setBackgroundColor(Color.red(argb) / 255f, Color.green(argb) / 255f,
                Color.blue(argb) / 255f, Color.alpha(argb) / 255f);
    }

    public void setBackgroundColor(float r, float g, float b, float a) {
        _textBackgroundR = r;
        _textBackgroundG = g;
        _textBackgroundB = b;
        _textBackgroundA = a;
    }

    public void setInsets(float left, float right, float bottom, float top) {
        _insetLeft = left;
        _insetRight = right;
        _insetBottom = bottom;
        _insetTop = top;
    }

    public void draw(GLMapView view) {
        update(view);
        if (!_drawText)
            return;

        // >> push
        GLES20FixedPipeline.glPushMatrix();

        float shiftx = 0f;
        float shifty = 0f;

        // if the label anchor point is on screen, but part of the text will be
        // cut off, shift it
        final float l = view._left + _insetLeft;
        final float r = view._right - _insetRight;
        final float b = view._bottom + _insetBottom;
        final float t = view._top - _insetTop;
        if (Rectangle.contains(view._left, view._bottom, view._right, view._top,
                _textPoint.x, _textPoint.y)) {
            if (_textPoint.x - _textWidth / 2f < l)
                shiftx = l - ((float) _textPoint.x - _textWidth / 2f);
            else if (_textPoint.x + _textWidth / 2f > r)
                shiftx = r - ((float) _textPoint.x - _textWidth / 2f);
            if (_textPoint.y - _textHeight / 2f < b)
                shifty = b - ((float) _textPoint.y - _textHeight / 2f);
            else if (_textPoint.y + _textHeight / 2f > t)
                shifty = t - ((float) _textPoint.y + _textHeight / 2f);
        }

        GLES20FixedPipeline.glTranslatef((float) _textPoint.x + shiftx,
                (float) _textPoint.y + shifty,
                (float) _textPoint.z);
        if (_rotateAlign)
            GLES20FixedPipeline.glRotatef(_textAngle, 0f, 0f, 1f);
        GLES20FixedPipeline.glTranslatef(-_textWidth / 2,
                -_textHeight / 2 + _glText.getDescent(), 0);
        // >> push
        if (_textBackgroundA > 0f) {
            if (_ninePatch == null)
                _ninePatch = GLRenderGlobals.get(view).getMediumNinePatch();
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(-8f,
                    -_glText.getDescent() - 4f, 0f);
            GLES20FixedPipeline.glColor4f(_textBackgroundR, _textBackgroundG,
                    _textBackgroundB, _textBackgroundA);
            if (_ninePatch != null)
                _ninePatch.draw(_textWidth + 16f, _textHeight + 8f);
            GLES20FixedPipeline.glPopMatrix();
        }
        // << pop

        for (int i = 0; i < _textLines.length; i++) {
            // >> push
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(0f,
                    ((_textLines.length - 1) - i)
                            * _glText.getCharHeight(),
                    0f);
            _glText.draw(_textLines[i], _textColorR, _textColorG,
                    _textColorB, _textColorA);
            GLES20FixedPipeline.glPopMatrix();
            // << pop
        }

        GLES20FixedPipeline.glPopMatrix();
        // << pop
    }

    void _validateText() {
        if (_text == null) {
            _textLines = null;
            _textWidth = 0f;
            _textHeight = 0f;
        } else {
            _textLines = _text.split("\n");
            if (_glText != null) {
                _textWidth = _glText.getStringWidth(_text);
                _textHeight = _glText.getStringHeight(_text);
            }
        }
    }

    public static float getSurfaceLabelOffset(GLMapView ortho,
            float textHeight) {
        // raise the label vertically above the surface as the
        // tilt increases. use sin^2 to temper adjustment velocity
        // during tilt
        final double sin_tilt = Math
                .sin(Math.toRadians(ortho.currentPass.drawTilt));
        return (float) (textHeight * sin_tilt * sin_tilt);
    }
}
