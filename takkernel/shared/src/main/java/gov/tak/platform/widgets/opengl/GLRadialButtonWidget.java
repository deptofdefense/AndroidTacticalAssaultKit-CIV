
package gov.tak.platform.widgets.opengl;

import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.android.maps.graphics.GLImageCache;

import gov.tak.api.commons.graphics.DisplaySettings;
import gov.tak.platform.engine.map.coords.ProjectionFactory;
import gov.tak.platform.graphics.Color;
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
            if (((IMapMenuButtonWidget) subject).getSubmenu() != null && !((IMapMenuButtonWidget) subject).isDisabled()) {
                draw_subm_arrow = true;
            }
            if (((IMapMenuButtonWidget) subject).isBackButton()) {
                draw_back_arrow = true;
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
            if (draw_back_arrow) {
                r = Color.red(SUNRISE_YELLOW) / 255f;
                g = Color.green(SUNRISE_YELLOW) / 255f;
                b = Color.blue(SUNRISE_YELLOW) / 255f;
                a = Color.alpha(SUNRISE_YELLOW) / 255f;
            }

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
        if (draw_subm_arrow) {
            // FFFFE35E
            _childWedge = _buildChildWedge(_span, _radius + _width, 6, 0d);
            float r = Color.red(SUNRISE_YELLOW) / 255f;
            float g = Color.green(SUNRISE_YELLOW) / 255f;
            float b = Color.blue(SUNRISE_YELLOW) / 255f;
            float a = Color.alpha(SUNRISE_YELLOW) / 255f;
            Shader shader = getDefaultShader();

            DrawState submDrawState = drawState.clone();
            Matrix.rotateM(submDrawState.modelMatrix, 0, (float) (_angle - _span / 2f), 0f,
                    0f, 1f);

            int prevProgram = shader.useProgram(true);

            shader.setColor4f(r, g, b, a);
            shader.setModelView(submDrawState.modelMatrix);
            shader.setProjection(submDrawState.projectionMatrix);

            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
            GLES30.glVertexAttribPointer(shader.getAVertexCoords(), 2, GLES30.GL_FLOAT, false, 0, _childWedge);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, _childWedge.capacity() / 2);

            GLES30.glDisableVertexAttribArray(shader.getAVertexCoords());
            GLES30.glDisable(GLES30.GL_BLEND);
            GLES30.glUseProgram(prevProgram);

            if(draw_subm_arrow) {
                if (_arrowImage == null && _subIconArrowCache != null
                        && _subIconArrowCache.getTextureId() != 0) {
                    int twidth = 16;
                    int theight = 16;
                    int tx = -(twidth / 2);
                    int ty = (theight / 2) - theight + 1;
                    float density = DisplaySettings.getDpi() / 80f;
                    _arrowImage = new GLImage(
                            _renderContext,
                            _subIconArrowCache.getTextureId(),
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
            }
            if (_arrowImage != null) {
                DrawState arrowDrawState = drawState.clone();
                Matrix.rotateM(arrowDrawState.modelMatrix, 0, (float)_angle, 0f, 0f, 1f);
                Matrix.translateM(arrowDrawState.modelMatrix, 0, (((float) _radius) + ((float) _width)) - 6f,
                        0f, 0f);
                Matrix.rotateM(arrowDrawState.modelMatrix, 0, (float)- 90f, 0f, 0f, 1f);
                int iconColor = 0xFFFFFFFF;
                r = Color.red(iconColor) / 255f;
                g = Color.green(iconColor) / 255f;
                b = Color.blue(iconColor) / 255f;
                a = Color.alpha(iconColor) / 255f;
                shader = getTextureShader();
                prevProgram = shader.useProgram(true);

                GLES30.glUniformMatrix4fv(shader.getUProjection(), 1, false, arrowDrawState.projectionMatrix, 0);
                GLES30.glUniformMatrix4fv(shader.getUModelView(), 1, false, arrowDrawState.modelMatrix, 0);
                _arrowImage.draw(shader, r, g, b, a);

                GLES30.glUseProgram(prevProgram);
            }
        }

        if (draw_back_arrow) {
            // FFFFE35E
            _childWedge = _buildChildWedge(_span, _radius, 6, 0d);

            float r = Color.red(SUNRISE_YELLOW) / 255f;
            float g = Color.green(SUNRISE_YELLOW) / 255f;
            float b = Color.blue(SUNRISE_YELLOW) / 255f;
            float a = Color.alpha(SUNRISE_YELLOW) / 255f;
            Shader shader = getDefaultShader();
            DrawState submDrawState = drawState.clone();
            Matrix.rotateM(submDrawState.modelMatrix, 0, (float) (_angle - _span / 2f), 0f,
                    0f, 1f);

            int prevProgram = shader.useProgram(true);
            shader.setColor4f(r, g, b, a);
            shader.setModelView(submDrawState.modelMatrix);
            shader.setProjection(submDrawState.projectionMatrix);

            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
            GLES30.glVertexAttribPointer(shader.getAVertexCoords(), 2, GLES30.GL_FLOAT, false, 0, _childWedge);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, _childWedge.capacity() / 2);

            GLES30.glDisableVertexAttribArray(shader.getAVertexCoords());
            GLES30.glDisable(GLES30.GL_BLEND);
            GLES30.glUseProgram(prevProgram);
        }
    }

    @Override
    public void drawWidget(DrawState drawState) {
        drawState = drawState.clone();
        //drawState.alphaMod = _subject.getDelayAlpha() / 255f;
        super.drawWidget(drawState);
        //if (_subject.isDelaying())
        //    getRenderContext().requestRefresh();

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

            // compute a text color that will be high contrast against the background. compute the
            // luminance of the background and set the text color to either black or white, per
            // whichever is furthest from the background color
            final float bg_r = android.graphics.Color.red(_bgColor)/255f;
            final float bg_g = android.graphics.Color.green(_bgColor)/255f;
            final float bg_b = android.graphics.Color.blue(_bgColor)/255f;
            final float bglum = 0.2126f*bg_r + 0.7152f*bg_g + 0.0722f*bg_b;
            final float textlum = (bglum>0.5f) ? 0f : 1f;

            Matrix.translateM(localDrawState.modelMatrix, 0, (float) -width / 2.f, 0, 0);
            glText.draw(_textValue,
                    textlum,
                    textlum,
                    textlum,
                    1f,
                    localDrawState.projectionMatrix, localDrawState.modelMatrix);
        }
    }

    @Override
    public void drawWidgetContent(DrawState drawState) {
        DrawState newDrawState = drawState.clone();
        IMapMenuButtonWidget menuSubject = null;
        if(subject instanceof IMapMenuButtonWidget)
            menuSubject = (IMapMenuButtonWidget)subject;

        newDrawState.alphaMod = menuSubject == null? 1f : menuSubject.getDelayAlpha() / 255f;
        super.drawWidgetContent(newDrawState);
        if (menuSubject != null && menuSubject.isDelaying())
            getRenderContext().requestRefresh();
        newDrawState.recycle();
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
    private FloatBuffer _buildChildWedge(double angle, double radius,
                                                                                double width,
                                                                                double spacing) {
        double cuts = Math.floor((72 / (360d / angle)));
        angle = (angle * Math.PI / 180d); // to radians

        double slices = cuts + 1;
        FloatBuffer b = GLTriangle.createFloatBuffer(2 * ((int) slices + 1), 2);

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

            b.put(i*4, x0);
            b.put(i * 4 + 1, y0);
            b.put(i * 4 + 2, x1);
            b.put(i * 4 + 3, y1);

            t0 += step0;
            t1 += step1;
        }
        b.position(0);

        return b;
    }

    private double _radius, _angle;
    private double _width, _span;
    private FloatBuffer _bg;
    private int _numPoints;
    private boolean _bgDirty;
    private boolean draw_subm_arrow = false;
    private boolean draw_back_arrow = false;
    private GLImageCache.Entry _subIconArrowCache;
    private FloatBuffer _childWedge;
    private GLImage _arrowImage;
    private final static int SUNRISE_YELLOW = 0xFFFFE35E;
}
