
package gov.tak.platform.widgets.opengl;
import android.graphics.Color;

import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.android.maps.graphics.GLImageCache;

import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IRadialButtonWidget;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.commons.opengl.GLES30;
import gov.tak.platform.commons.opengl.Matrix;
import gov.tak.api.engine.Shader;

import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IMapWidget;

import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLText;

import java.nio.FloatBuffer;

public class GLRadialButtonWidget extends GLAbstractButtonWidget implements
        IRadialButtonWidget.OnSizeChangedListener, IRadialButtonWidget.OnOrientationChangedListener {

    public final static IGLWidgetSpi SPI = new IGLWidgetSpi() {
        @Override
        public int getPriority() {
            // RadialButtonWidget : AbstractButtonWidget : MapWidget
            return 2;
        }

        @Override
        public GLWidget create(MapRenderer renderContext, IMapWidget subject) {
            if (subject instanceof IRadialButtonWidget) {
                IRadialButtonWidget radialButton = (IRadialButtonWidget) subject;
                GLRadialButtonWidget glRadialButton = new GLRadialButtonWidget(
                        radialButton, renderContext);

                return glRadialButton;
            } else {
                return null;
            }
        }
    };

    public GLRadialButtonWidget(IRadialButtonWidget subject,
            MapRenderer orthoView) {
        super(subject, orthoView);
        _angle = subject.getOrientationAngle();
        _span = subject.getButtonSpan();
        _width = subject.getButtonWidth();
        _radius = subject.getOrientationRadius();
        _bgDirty = true;
        if (subject instanceof IMapMenuButtonWidget) {
            if (((IMapMenuButtonWidget) subject).getSubmenu() != null) {
                draw_subm_arrow = true;
            }
        }
    }

    @Override
    public void start() {
        super.start();
        if (subject instanceof IRadialButtonWidget) {
            IRadialButtonWidget rbw = (IRadialButtonWidget) subject;
            rbw.addOnOrientationChangedListener(this);
            rbw.addOnSizeChangedListener(this);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (subject instanceof IRadialButtonWidget) {
            IRadialButtonWidget rbw = (IRadialButtonWidget) subject;
            rbw.removeOnOrientationChangedListener(this);
            rbw.removeOnSizeChangedListener(this);
        }
    }

    @Override
    void drawButtonBackground(DrawState drawState, int bgColor) {
        if (bgColor != 0) {

            if (_bgDirty) {
                _buildRingWedge(_span, _radius, _width, 5d);
               // _buildRingWedge(_span, _radius, _width, 5d);
                _bgDirty = false;
            }

            GLES30.glEnable(GLES30.GL_BLEND);
            float r = Color.red(bgColor) / 255f;
            float g = Color.green(bgColor) / 255f;
            float b = Color.blue(bgColor) / 255f;
            float a = Color.alpha(bgColor) / 255f;

            Shader shader = getDefaultShader();

            int prevProgram = shader.useProgram(true);
            shader.setColor4f(r, g, b, a);
            DrawState localDrawState = drawState.clone();
            Matrix.rotateM(localDrawState.modelMatrix, 0, (float) (_angle - _span / 2f), 0f,
                    0f, 1f);

            shader.setModelView(localDrawState.modelMatrix);

            shader.setProjection(localDrawState.projectionMatrix);

            GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
            _bg.position(0);
            GLES30.glVertexAttribPointer(shader.getAVertexCoords(), 2, GLES30.GL_FLOAT, false, 0, _bg);

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, _numPoints);

            GLES30.glDisable(GLES30.GL_BLEND);
            GLES30.glDisableVertexAttribArray(shader.getAVertexCoords());
            GLES30.glUseProgram(prevProgram);

            localDrawState.recycle();
        }
    }

    @Override
    void drawButtonIcon(DrawState drawState, int iconColor, GLImage iconImage) {
        DrawState localDrawState = drawState.clone();
        Matrix.rotateM(localDrawState.modelMatrix, 0, (float) _angle, 0f,
                0f, 1f);
        Matrix.translateM(localDrawState.modelMatrix, 0, (float) (_radius + _width / 2), 0f, 0f);
        Matrix.rotateM(localDrawState.modelMatrix, 0, (float) -_angle, 0f,
                0f, 1f);

        float r = Color.red(iconColor) / 255f;
        float g = Color.green(iconColor) / 255f;
        float b = Color.blue(iconColor) / 255f;
        float a = Color.alpha(iconColor) / 255f;

        Shader shader = getTextureShader();

        int prevProgram = shader.useProgram(true);
        GLES30.glUniformMatrix4fv(shader.getUProjection(), 1, false, localDrawState.projectionMatrix, 0);
        GLES30.glUniformMatrix4fv(shader.getUModelView(), 1, false, localDrawState.modelMatrix, 0);
        GLES30.glUniform4f(shader.getUColor(), r, g, b, a);

        _image.draw(shader, r, g, b, a);
        GLES30.glUseProgram(prevProgram);

        if (draw_subm_arrow) {

            if (_arrowImage == null && _subIconArrowCache != null
                    && _subIconArrowCache.getTextureId() != 0) {
                int twidth = 16;
                int theight = 16;
                int tx = -8;
                int ty = 8 - theight + 1;
                _arrowImage = new GLImage(
                        _renderContext,
                        _subIconArrowCache.getTextureId(),
                        _subIconArrowCache.getTextureWidth(),
                        _subIconArrowCache.getTextureHeight(),
                        _subIconArrowCache.getImageTextureX(),
                        _subIconArrowCache.getImageTextureY(),
                        _subIconArrowCache.getImageTextureWidth(),
                        _subIconArrowCache.getImageTextureHeight(),
                        tx * GLRenderGlobals.getRelativeScaling(),
                        ty * GLRenderGlobals.getRelativeScaling(),
                        twidth * GLRenderGlobals.getRelativeScaling(),
                        theight * GLRenderGlobals.getRelativeScaling());
            }
            if (_arrowImage != null) {
                DrawState arrowDrawState = drawState.clone();
                Matrix.rotateM(arrowDrawState.modelMatrix, 0, (float)_angle, 0f, 0f, 1f);
                Matrix.translateM(arrowDrawState.modelMatrix, 0, (float) (_radius + (_width / 2) + (_width / 4)),
                        0f, 0f);
                Matrix.rotateM(arrowDrawState.modelMatrix, 0, (float)- 90f, 0f, 0f, 1f);
                r = Color.red(iconColor) / 255f;
                g = Color.green(iconColor) / 255f;
                b = Color.blue(iconColor) / 255f;
                a = Color.alpha(iconColor) / 255f;
                prevProgram = shader.useProgram(true);

                GLES30.glUniformMatrix4fv(shader.getUProjection(), 1, false, arrowDrawState.projectionMatrix, 0);
                GLES30.glUniformMatrix4fv(shader.getUModelView(), 1, false, arrowDrawState.modelMatrix, 0);
                GLES30.glUniform4f(shader.getUColor(), r, g, b, a);
                _arrowImage.draw(shader);

                GLES30.glUseProgram(prevProgram);
            }
        }

        localDrawState.recycle();
    }

    @Override
    void drawButtonText(DrawState drawState, GLText glText, String _textValue) {
        if (_textValue != null && _textValue.length() > 0) {
            DrawState localDrawState = drawState.clone();
            Matrix.rotateM(localDrawState.modelMatrix, 0, (float) this._angle, 0.0F, 0.0F, 1.0F);
            Matrix.translateM(localDrawState.modelMatrix, 0, (float) (this._radius + this._width / 2.0D), 0.0F, 0.0F);
            Matrix.rotateM(localDrawState.modelMatrix, 0, (float) (-this._angle), 0.0F, 0.0F, 1.0F);

            int width = GLRenderGlobals.getDefaultTextFormat().measureTextWidth(_textValue);

            Matrix.translateM(localDrawState.modelMatrix, 0, (float) -width / 2.f, 0, 0);
            glText.draw(_textValue, .2f, .2f, .2f, 1, localDrawState.projectionMatrix, localDrawState.modelMatrix);
        }
    }

    @Override
    public void onRadialButtonSizeChanged(IRadialButtonWidget button) {
        final double span = button.getButtonSpan();
        final double width = button.getButtonWidth();
        runOrQueueEvent(new Runnable() {
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
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _angle = angle;
                _radius = radius;
                _bgDirty = true;
            }
        });
    }

    private void _buildRingWedge(double angle, double radius,
            double width,
            double spacing) {
        double cuts = Math.floor((72 / (360d / angle)));
        angle = (angle * Math.PI / 180d); // to radians

        double slices = cuts + 1;
        _numPoints = 2 * ((int) slices + 1);
        _bg = gov.tak.platform.widgets.opengl.GLTriangle.createFloatBuffer(_numPoints, 2);

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

            _bg.put(i * 4, x0);
            _bg.put(i * 4 + 1, y0);
            _bg.put(i * 4 + 2, x1);
            _bg.put(i * 4 + 3, y1);

            t0 += step0;
            t1 += step1;
        }
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

            final GLImageCache imageCache = GLRenderGlobals.get(getRenderContext())
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
        runOrQueueEvent(new Runnable() {
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
    private FloatBuffer _bg;
    private int _numPoints;
    private boolean _bgDirty;
    private boolean draw_subm_arrow = false;
    private GLImageCache.Entry _subIconArrowCache;
    private GLImage _arrowImage;
}
