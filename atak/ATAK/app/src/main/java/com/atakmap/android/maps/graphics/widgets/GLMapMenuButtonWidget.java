
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.menu.MapMenuButtonWidget;
import com.atakmap.android.widgets.AbstractButtonWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.RadialButtonWidget;
import com.atakmap.android.widgets.WidgetIcon;
import com.atakmap.android.widgets.WidgetBackground;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;

import androidx.core.graphics.ColorUtils;

import gov.tak.api.widgets.IRadialButtonWidget;

/**
 * Widget used to render the new style radial buttons
 */
public class GLMapMenuButtonWidget extends GLAbstractButtonWidget implements
        IRadialButtonWidget.OnSizeChangedListener,
        IRadialButtonWidget.OnOrientationChangedListener {

    private final static int SUNRISE_YELLOW = 0xFFFFE35E;
    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            return 3;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof MapMenuButtonWidget) {
                MapMenuButtonWidget radialButton = (MapMenuButtonWidget) subject;
                GLMapMenuButtonWidget glRadialButton = new GLMapMenuButtonWidget(
                        radialButton, orthoView);
                glRadialButton.startObserving(radialButton);

                return glRadialButton;
            } else {
                return null;
            }
        }
    };

    private final static double _buttonIconScaler = 1.5;

    private final MapMenuButtonWidget _subject;
    private double _radius, _angle;
    private double _width, _span;
    private GLTriangle.Strip _bg;
    private GLTriangle.Strip _childWedge;
    private boolean _bgDirty;
    private boolean draw_subm_arrow = false;
    private boolean draw_back_arrow = false;
    private GLImageCache.Entry _subIconArrowCache;

    private WidgetIcon icon;

    private GLImage _arrowImage;

    public GLMapMenuButtonWidget(MapMenuButtonWidget subject,
            GLMapView orthoView) {
        super(subject, orthoView);
        _subject = subject;
        _angle = subject.getOrientationAngle();
        _span = subject.getButtonSpan();
        _width = subject.getButtonWidth();
        _radius = subject.getOrientationRadius();
        _bgDirty = true;
        if (subject.getSubmenu() != null && !subject.isDisabled())
            draw_subm_arrow = true;
        if (subject.isBackButton())
            draw_back_arrow = true;
    }

    @Override
    public void onButtonBackgroundChanged(AbstractButtonWidget button) {
        super.onButtonBackgroundChanged(button);
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof MapMenuButtonWidget) {
            MapMenuButtonWidget rbw = (MapMenuButtonWidget) subject;
            rbw.addOnOrientationChangedListener(this);
            rbw.addOnSizeChangedListener(this);
            rbw.addOnBackgroundChangedListener(this);
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
    public void drawWidgetContent() {
        GLES20FixedPipeline.glPushAlphaMod(_subject.getDelayAlpha() / 255f);
        super.drawWidgetContent();
        GLES20FixedPipeline.glPopAlphaMod();
        if (_subject.isDelaying())
            getSurface().requestRefresh();
    }

    @Override
    public void drawButtonBackground(int bgColor) {
        if (bgColor != 0) {

            if (_bgDirty) {
                _bg = _buildRingWedge(_span, _radius, _width, 0d);
                _bgDirty = false;
            }

            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            float r = Color.red(bgColor) / 255f;
            float g = Color.green(bgColor) / 255f;
            float b = Color.blue(bgColor) / 255f;
            float a = Color.alpha(bgColor) / 255f;
            if (draw_back_arrow) {
                r = Color.red(SUNRISE_YELLOW) / 255f;
                g = Color.green(SUNRISE_YELLOW) / 255f;
                b = Color.blue(SUNRISE_YELLOW) / 255f;
                a = Color.alpha(SUNRISE_YELLOW) / 255f;
            }
            GLES20FixedPipeline.glColor4f(r, g, b, a);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glRotatef((float) (_angle - _span / 2f), 0f,
                    0f, 1f);
            _bg.draw();
            GLES20FixedPipeline.glPopMatrix();
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        }

        if (draw_subm_arrow) {
            // FFFFE35E
            _childWedge = _buildChildWedge(_span, _radius + _width, 6, 0d);

            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            float r = Color.red(SUNRISE_YELLOW) / 255f;
            float g = Color.green(SUNRISE_YELLOW) / 255f;
            float b = Color.blue(SUNRISE_YELLOW) / 255f;
            float a = Color.alpha(SUNRISE_YELLOW) / 255f;
            GLES20FixedPipeline.glColor4f(r, g, b, a);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glRotatef((float) (_angle - _span / 2f), 0f,
                    0f, 1f);
            _childWedge.draw();
            GLES20FixedPipeline.glPopMatrix();
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        }

        if (draw_back_arrow) {
            // FFFFE35E
            _childWedge = _buildChildWedge(_span, _radius, 6,
                    0d);

            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            float r = Color.red(SUNRISE_YELLOW) / 255f;
            float g = Color.green(SUNRISE_YELLOW) / 255f;
            float b = Color.blue(SUNRISE_YELLOW) / 255f;
            float a = Color.alpha(SUNRISE_YELLOW) / 255f;
            GLES20FixedPipeline.glColor4f(r, g, b, a);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glRotatef((float) (_angle - _span / 2f), 0f,
                    0f, 1f);
            _childWedge.draw();
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

        iconImage.draw(r, g, b, a);
        GLES20FixedPipeline.glPopMatrix();

        if (draw_subm_arrow) {

            if (_arrowImage == null && _subIconArrowCache != null
                    && _subIconArrowCache.getTextureId() != 0) {
                int twidth = 16;
                int theight = 16;
                int tx = -8;
                int ty = -8;
                float density = (float) (orthoView.getSurface().getMapView()
                        .getDisplayDpi() / 80d);
                _arrowImage = new GLImage(_subIconArrowCache.getTextureId(),
                        _subIconArrowCache.getTextureWidth(),
                        _subIconArrowCache.getTextureHeight(),
                        _subIconArrowCache.getImageTextureX(),
                        _subIconArrowCache.getImageTextureY(),
                        _subIconArrowCache.getImageTextureWidth(),
                        _subIconArrowCache.getImageTextureHeight(),
                        tx * density,
                        ty * density,
                        twidth * density,
                        theight * density);
            }
            if (_arrowImage != null) {
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glRotatef((float) _angle, 0f, 0f, 1f);
                GLES20FixedPipeline
                        .glTranslatef(
                                (((float) _radius) + ((float) _width)) - 6f,
                                0f, 0f);
                GLES20FixedPipeline.glRotatef((float) -90, 0f, 0f, 1f);
                r = Color.red(Color.BLACK) / 255f;
                g = Color.green(Color.BLACK) / 255f;
                b = Color.blue(Color.BLACK) / 255f;
                a = Color.alpha(Color.BLACK) / 255f;
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
    public void onButtonStateChanged(AbstractButtonWidget button) {

        final WidgetBackground background = button.getBackground();
        if (background != null) {
            switch (button.getState()) {
                case AbstractButtonWidget.STATE_PRESSED:
                case AbstractButtonWidget.STATE_SELECTED:
                    _iconColor = Color.BLACK;
                    break;
                case AbstractButtonWidget.STATE_DISABLED:
                    _iconColor = ColorUtils.setAlphaComponent(
                            button.getBackground().getColor(
                                    AbstractButtonWidget.STATE_PRESSED),
                            128);
                    break;
                default:
                    _iconColor = button.getBackground()
                            .getColor(AbstractButtonWidget.STATE_PRESSED);
                    break;
            }
        } else {
            // make the broken state more obvious
            _iconColor = Color.CYAN;
        }
        super.onButtonStateChanged(button);
    }

    @Override
    public void onRadialButtonSizeChanged(IRadialButtonWidget button) {
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
    public void onRadialButtonOrientationChanged(IRadialButtonWidget button) {
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

    private GLTriangle.Strip _buildChildWedge(double angle, double radius,
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
                    "asset://icons/sub_menu_indicator.png", false);
            _subIconArrowCache = imageCache.fetchAndRetain(
                    "asset://icons/sub_menu_indicator.png",
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

    @Override
    public void onButtonIconChanged(AbstractButtonWidget button) {

        int width = (int) (button.getIcon().getIconWidth() * _buttonIconScaler);
        int height = (int) (button.getIcon().getIconHeight()
                * _buttonIconScaler);
        int anchorX = button.getIcon().getAnchorX()
                + ((width - button.getIcon().getIconWidth()) / 2);
        int anchorY = button.getIcon().getAnchorY()
                + ((height - button.getIcon().getIconHeight()) / 2);

        if (icon == button.getIcon()) {
            icon = new WidgetIcon.Builder()
                    .setAnchor(anchorX, anchorY)
                    .setSize(width, height)
                    .setImageRef(button.getState(),
                            button.getIcon().getIconRef(button.getState()))
                    .build();
            button.setIcon(icon);
        }
        super.onButtonIconChanged(button);
    }

}
