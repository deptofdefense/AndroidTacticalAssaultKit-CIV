
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.menu.MapMenuButtonWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.RadialButtonWidget;
import com.atakmap.android.widgets.RadialButtonWidget.OnOrientationChangedListener;
import com.atakmap.android.widgets.RadialButtonWidget.OnSizeChangedListener;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLRadialButtonWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLRadialButtonWidget extends GLAbstractButtonWidget implements
        OnSizeChangedListener, OnOrientationChangedListener {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            // RadialButtonWidget : AbstractButtonWidget : MapWidget
            return 2;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof RadialButtonWidget) {
                RadialButtonWidget radialButton = (RadialButtonWidget) subject;
                GLRadialButtonWidget glRadialButton = new GLRadialButtonWidget(
                        radialButton, orthoView);
                glRadialButton.startObserving(radialButton);

                return glRadialButton;
            } else {
                return null;
            }
        }
    };

    public GLRadialButtonWidget(RadialButtonWidget subject,
            GLMapView orthoView) {
        super(subject, orthoView);
        _angle = subject.getOrientationAngle();
        _span = subject.getButtonSpan();
        _width = subject.getButtonWidth();
        _radius = subject.getOrientationRadius();
        _bgDirty = true;
        if (subject instanceof MapMenuButtonWidget) {
            if (((MapMenuButtonWidget) subject).getSubmenuWidget() != null) {
                draw_subm_arrow = true;
            }
        }
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof RadialButtonWidget) {
            RadialButtonWidget rbw = (RadialButtonWidget) subject;
            rbw.addOnOrientationChangedListener(this);
            rbw.addOnSizeChangedListener(this);
        }
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof RadialButtonWidget) {
            RadialButtonWidget rbw = (RadialButtonWidget) subject;
            rbw.removeOnOrientationChangedListener(this);
            rbw.removeOnSizeChangedListener(this);
        }
    }

    @Override
    public void drawButtonBackground(int bgColor) {
        if (bgColor != 0) {

            if (_bgDirty) {
                _bg = _buildRingWedge(_span, _radius, _width, 5d);
                _bgDirty = false;
            }

            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            float r = Color.red(bgColor) / 255f;
            float g = Color.green(bgColor) / 255f;
            float b = Color.blue(bgColor) / 255f;
            float a = Color.alpha(bgColor) / 255f;
            GLES20FixedPipeline.glColor4f(r, g, b, a);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glRotatef((float) (_angle - _span / 2f), 0f,
                    0f, 1f);
            _bg.draw();
            GLES20FixedPipeline.glPopMatrix();
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        }
    }

    @Override
    public void drawButtonIcon(int iconColor, GLImage iconImage) {
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glRotatef((float) _angle, 0f, 0f, 1f);
        GLES20FixedPipeline
                .glTranslatef((float) (_radius + _width / 2), 0f, 0f);
        GLES20FixedPipeline.glRotatef((float) -_angle, 0f, 0f, 1f);
        float r = Color.red(iconColor) / 255f;
        float g = Color.green(iconColor) / 255f;
        float b = Color.blue(iconColor) / 255f;
        float a = Color.alpha(iconColor) / 255f;
        GLES20FixedPipeline.glColor4f(r, g, b, a);
        iconImage.draw();
        GLES20FixedPipeline.glPopMatrix();

        if (draw_subm_arrow) {

            if (_arrowImage == null && _subIconArrowCache != null
                    && _subIconArrowCache.getTextureId() != 0) {
                int twidth = 16;
                int theight = 16;
                int tx = -8;
                int ty = 8 - theight + 1;
                _arrowImage = new GLImage(_subIconArrowCache.getTextureId(),
                        _subIconArrowCache.getTextureWidth(),
                        _subIconArrowCache.getTextureHeight(),
                        _subIconArrowCache.getImageTextureX(),
                        _subIconArrowCache.getImageTextureY(),
                        _subIconArrowCache.getImageTextureWidth(),
                        _subIconArrowCache.getImageTextureHeight(),
                        tx * MapView.DENSITY,
                        ty * MapView.DENSITY,
                        twidth * MapView.DENSITY,
                        theight * MapView.DENSITY);
            }
            if (_arrowImage != null) {
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glRotatef((float) _angle, 0f, 0f, 1f);
                GLES20FixedPipeline
                        .glTranslatef(
                                (float) (_radius + (_width / 2) + (_width / 4)),
                                0f, 0f);
                GLES20FixedPipeline.glRotatef((float) -90, 0f, 0f, 1f);
                r = Color.red(iconColor) / 255f;
                g = Color.green(iconColor) / 255f;
                b = Color.blue(iconColor) / 255f;
                a = Color.alpha(iconColor) / 255f;
                GLES20FixedPipeline.glColor4f(r, g, b, a);
                _arrowImage.draw();
                GLES20FixedPipeline.glPopMatrix();
            }

        }

    }

    @Override
    public void drawButtonText(GLText glText, String _textValue) {

    }

    @Override
    public void onRadialButtonSizeChanged(RadialButtonWidget button) {
        final double span = button.getButtonSpan();
        final double width = button.getButtonWidth();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _span = span;
                _width = width;
                _bgDirty = true;
            }
        });
    }

    @Override
    public void onRadialButtonOrientationChanged(RadialButtonWidget button) {
        final double radius = button.getOrientationRadius();
        final double angle = button.getOrientationAngle();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _angle = angle;
                _radius = radius;
                _bgDirty = true;
            }
        });
    }

    private GLTriangle.Strip _buildRingWedge(double angle, double radius,
            double width,
            double spacing) {
        double cuts = Math.floor((72 / (360d / angle)));
        angle = (angle * Math.PI / 180d); // to radians

        double slices = cuts + 1;
        GLTriangle.Strip b = new GLTriangle.Strip(2, 2 * ((int) slices + 1));

        double r1 = radius + width;

        double a0 = (angle * radius - spacing) / radius;
        double a1 = (angle * r1 - spacing) / r1;

        double step0 = a0 / slices;
        double step1 = a1 / slices;

        double t0 = 0d;
        double t1 = 0d;

        for (int i = 0; i < slices + 1; ++i) {

            float x0 = (float) (radius * Math.cos(t0));
            float x1 = (float) (r1 * Math.cos(t1));
            float y0 = (float) (radius * Math.sin(t0));
            float y1 = (float) (r1 * Math.sin(t1));

            b.setX(i * 2, x0);
            b.setY(i * 2, y0);
            b.setX(i * 2 + 1, x1);
            b.setY(i * 2 + 1, y1);

            t0 += step0;
            t1 += step1;
        }

        return b;
    }

    @Override
    protected void _updateIconRef(String uri) {
        super._updateIconRef(uri);

        if (_subIconArrowCache == null
                || !_subIconArrowCache.getUri().equals(uri)) {

            // done with it
            if (_subIconArrowCache != null) {
                _subIconArrowCache.release();
                _subIconArrowCache = null;
            }

            final GLImageCache imageCache = GLRenderGlobals.get(getSurface())
                    .getImageCache();

            imageCache.prefetch(
                    "asset://icons/subm_arrow.png", false);
            _subIconArrowCache = imageCache.fetchAndRetain(
                    "asset://icons/subm_arrow.png",
                    false);

            _arrowImage = null;

        }
    }

    @Override
    public void releaseWidget() {
        super.releaseWidget();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                if (_subIconArrowCache != null) {
                    _subIconArrowCache.release();
                    _subIconArrowCache = null;
                }
                _arrowImage = null;
            }
        });
    }

    private double _radius, _angle;
    private double _width, _span;
    private GLTriangle.Strip _bg;
    private boolean _bgDirty;
    private boolean draw_subm_arrow = false;
    private GLImageCache.Entry _subIconArrowCache;
    private GLImage _arrowImage;
}
