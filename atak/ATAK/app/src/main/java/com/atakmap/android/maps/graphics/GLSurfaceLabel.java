
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.Typeface;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.annotations.IncubatingApi;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

/** @deprecated DO NOT USE, interim code consolidation pending label manager */
@IncubatingApi(since = "4.3")
@Deprecated
@DeprecatedApi(since = "4.3", forRemoval = true, removeAt = "4.4")
final class GLSurfaceLabel {
    final static String TAG = "GLSegmentFloatingLabel";

    GeoPoint _pt;
    GeoPoint _relativeScaleEast;
    GeoPoint _relativeScaleNorth;
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
    boolean _rotateAbsolute = true;

    float _offsetX;
    float _offsetY;

    float _insetLeft = 0f;
    float _insetRight = 0f;
    float _insetBottom = 0f;
    float _insetTop = 0f;

    public void update(GLMapView ortho) {
        if (ortho.currentPass.drawVersion == _version)
            return;
        _version = ortho.currentPass.drawVersion;

        // not enough points supplied - do not attempt to draw
        if (_pt == null)
            return;

        if (_glText == null) {
            _glText = GLText.getInstance(MapView.getTextFormat(
                    Typeface.DEFAULT, +2));
            _validateText();
        }

        _relativeScaleEast = GeoCalculations.pointAtDistance(_pt, 90,
                _textWidth / 2f * ortho.currentScene.drawMapResolution);
        _relativeScaleNorth = GeoCalculations.pointAtDistance(_pt, 0,
                _textHeight / 2f * ortho.currentScene.drawMapResolution);

        ortho.currentPass.scene.forward(_pt, _textPoint);
        _drawText = true;
    }

    public void getTextPoint(PointD point) {
        point.x = _textPoint.x;
        point.y = _textPoint.y;
        point.z = _textPoint.z;
    }

    public void setRotation(float angle, boolean absolute) {
        _rotateAbsolute = absolute;
        _textAngle = angle;
        _version = -1;
    }

    public void setLocation(GeoPoint pt) {
        _pt = pt;
        _version = -1;
    }

    public void setTextOffset(float tx, float ty) {
        _offsetX = tx;
        _offsetY = ty;
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
        if (false) {
            final float l = view._left + _insetLeft;
            final float r = view._right - _insetRight;
            final float b = view._bottom + _insetBottom;
            final float t = view._top - _insetTop;
            if (Rectangle.contains(view._left, view._bottom, view._right,
                    view._top, _textPoint.x, _textPoint.y)) {
                if (_textPoint.x - _textWidth / 2f < l)
                    shiftx = l - ((float) _textPoint.x - _textWidth / 2f);
                else if (_textPoint.x + _textWidth / 2f > r)
                    shiftx = r - ((float) _textPoint.x - _textWidth / 2f);
                if (_textPoint.y - _textHeight / 2f < b)
                    shifty = b - ((float) _textPoint.y - _textHeight / 2f);
                else if (_textPoint.y + _textHeight / 2f > t)
                    shifty = t - ((float) _textPoint.y + _textHeight / 2f);
            }
        }

        GLES20FixedPipeline.glTranslatef(
                (float) _textPoint.x + _offsetX + shiftx,
                (float) _textPoint.y + _offsetY + shifty,
                (float) _textPoint.z);
        float rotation = _textAngle;
        if (_rotateAbsolute)
            rotation -= view.currentPass.drawRotation;
        else
            rotation += view.currentScene.drawRotation;

        //make sure not to display upside down text views
        double totalRot = Math.abs(rotation - view.currentScene.drawRotation)
                % 360d;
        if (totalRot > 90 && totalRot < 270)
            rotation = (rotation + 180) % 360;

        GLES20FixedPipeline.glRotatef(-1f * rotation, 0f, 0f, 1f);

        // compute relative scaling for the current view
        final float ssv = (float) orthoScreenSpaceDistance(view,
                _relativeScaleNorth.getLatitude(),
                _relativeScaleNorth.getLongitude(),
                _pt.getLatitude(), _pt.getLongitude());
        final float ssh = (float) orthoScreenSpaceDistance(view,
                _relativeScaleEast.getLatitude(),
                _relativeScaleEast.getLongitude(),
                _pt.getLatitude(), _pt.getLongitude());

        final float relativeScaleX = ssh / (_textWidth / 2f);
        final float relativeScaleY = ssv / (_textHeight / 2f);

        GLES20FixedPipeline.glScalef(relativeScaleX, relativeScaleY, 1f);

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
