
package com.atakmap.android.targetbubble.graphics;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.targetbubble.CrosshairLayer;
import com.atakmap.android.targetbubble.MapTargetBubble;
import com.atakmap.android.targetbubble.MapTargetBubble.OnCrosshairColorChangedListener;
import com.atakmap.android.targetbubble.MapTargetBubble.OnLocationChangedListener;
import com.atakmap.android.targetbubble.MapTargetBubble.OnScaleChangedListener;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.Globe;
import com.atakmap.map.LegacyAdapters;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.opengl.GLAbstractLayer2;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayer3;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;

public class GLMapTargetBubble extends GLMapView implements
        OnLocationChangedListener,
        OnScaleChangedListener,
        OnCrosshairColorChangedListener,
        GLLayer3,
        MapControl {

    static {
        GLLayerFactory.register(GLTargetBubbleCrosshair.SPI);
    }

    public final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // MapTargetBubble : Layer
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (layer instanceof MapTargetBubble)
                return new GLMapTargetBubble(surface, (MapTargetBubble) layer);
            return null;
        }
    };

    /**
     * also refered to as the reticle tool
     */

    public static final String TAG = "GLMapTargetBubble";

    private static final int _STENCIL_CIRCLE_POINT_COUNT = 48;

    private final double circleRadius;

    private final MapTargetBubble subject;

    private boolean initialized;

    private final MapRenderer renderCtx;

    private Polygon viewport;

    private final GLMapView parent;

    private GLTargetBubbleCrosshair crosshair;

    private float subjectLeft;
    private float subjectTop;
    private float subjectRight;
    private float subjectBottom;

    public GLMapTargetBubble(final MapRenderer surface,
            MapTargetBubble subject) {
        super(LegacyAdapters.getRenderContext(surface), subject.getGlobe(),
                ((GLMapView) surface)._left,
                ((GLMapView) surface)._bottom,
                ((GLMapView) surface)._right,
                ((GLMapView) surface)._top);

        this.subjectLeft = subject.getX();
        this.subjectTop = ((GLMapView) surface).getSurface().getHeight()
                - subject.getY();
        this.subjectRight = subject.getX() + subject.getWidth();
        this.subjectBottom = ((GLMapView) surface).getSurface().getHeight()
                - subject.getY()
                - subject.getHeight();

        this.renderCtx = surface;
        this.parent = (GLMapView) this.renderCtx;
        this.subject = subject;

        this.setTargeting(this.subject.isCoordExtractionBubble());

        final double lat = subject.getLatitude();
        final double lng = subject.getLongitude();
        circleRadius = subject.getWidth() / 2d;
        viewport = subject.getViewport();

        this.setFocusPointOffset(((GLMapView) surface).getFocusPointOffsetX(),
                ((GLMapView) surface).getFocusPointOffsetY());

        this.initialized = false;
    }

    @Override
    public final void render() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onMapTargetBubbleLocationChanged(final MapTargetBubble bubble) {
        this.lookAt(new GeoPoint(bubble.getLatitude(), bubble.getLongitude()),
                Globe.getMapResolution(getRenderSurface().getDpi(),
                        bubble.getMapScale()),
                0d, 0d, false);
    }

    @Override
    public void onMapTargetBubbleScaleChanged(MapTargetBubble bubble) {
        this.lookAt(new GeoPoint(bubble.getLatitude(), bubble.getLongitude()),
                Globe.getMapResolution(getRenderSurface().getDpi(),
                        bubble.getMapScale()),
                0d, 0d, false);
    }

    @Override
    public void onMapTargetBubbleCrosshairColorChanged(MapTargetBubble bubble) {
    }

    @Override
    public void draw(GLMapView view) {
        this.draw(view, -1);
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if (!MathUtils.hasBits(renderPass, getRenderPass()))
            return;

        if (!this.initialized) {
            if (this.viewport != null) {
                /* create the stencil buffer circle */
                _circle = new GLTriangle.Fan(2, _STENCIL_CIRCLE_POINT_COUNT);
                double angleStep = 2 * Math.PI / _STENCIL_CIRCLE_POINT_COUNT;
                for (int i = 0; i < _STENCIL_CIRCLE_POINT_COUNT; ++i) {
                    double angle = i * angleStep;
                    double cx = circleRadius * Math.cos(angle);
                    double cy = circleRadius * Math.sin(angle);
                    _circle.setX(i, (float) cx);
                    _circle.setY(i, (float) cy);
                }
            }
            this.crosshair = new GLTargetBubbleCrosshair(this,
                    this.subject.getCrosshair());
            this.crosshair.start();
            this.initialized = true;
        }

        if (_circle != null) {
            GLES20FixedPipeline
                    .glClear(GLES20FixedPipeline.GL_STENCIL_BUFFER_BIT);
            GLES20FixedPipeline.glStencilMask(0xFFFFFFFF);
            GLES20FixedPipeline.glStencilFunc(GLES20FixedPipeline.GL_ALWAYS,
                    0x1,
                    0x1);
            GLES20FixedPipeline.glStencilOp(GLES20FixedPipeline.GL_KEEP,
                    GLES20FixedPipeline.GL_KEEP,
                    GLES20FixedPipeline.GL_INCR);
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_STENCIL_TEST);
            GLES20FixedPipeline.glColorMask(false, false, false, false);

            final float right = subjectRight;
            final float left = subjectLeft;
            final float bottom = subjectBottom;
            final float top = subjectTop;

            final float width = right - left;
            final float height = top - bottom;

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(left + width / 2, bottom + height
                    / 2,
                    0);
            GLES20FixedPipeline.glColor4f(1f, 0f, 0f, 1f);
            _circle.draw();
            GLES20FixedPipeline.glPopMatrix();

            GLES20FixedPipeline.glStencilMask(0xFFFFFFFF);
            GLES20FixedPipeline.glStencilFunc(GLES20FixedPipeline.GL_EQUAL,
                    0x1,
                    0x1);
            GLES20FixedPipeline.glStencilOp(GLES20FixedPipeline.GL_KEEP,
                    GLES20FixedPipeline.GL_KEEP,
                    GLES20FixedPipeline.GL_KEEP);
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_STENCIL_TEST);
            GLES20FixedPipeline.glColorMask(true, true, true, true);
        }
        super.render();
        if (_circle != null) {
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_STENCIL_TEST);
        }
        crosshair.draw(this, RENDER_PASS_UI);
    }

    @Override
    public void release() {
        Collection<GLMapRenderable> renderables = new LinkedList<>();
        super.getMapRenderables(renderables);
        for (GLMapRenderable r : renderables) {
            if (r instanceof GLLayer3)
                ((GLLayer3) r).stop();
            r.release();
        }
        if (crosshair != null) {
            crosshair.stop();
            crosshair.release();
            this.crosshair = null;
        }
        super.release();
        super.dispose();

        _circle = null;
        this.initialized = false;
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_UI;
    }

    @Override
    public void startAnimating(double lat, double lng, double scale,
            double rotation, double tilt, double animateFactor) {
    }

    /**************************************************************************/
    // GL Layer

    @Override
    public Layer getSubject() {
        return this.subject;
    }

    @Override
    public void start() {
        this.subject.addOnLocationChangedListener(this);
        this.subject.addOnScaleChangedListener(this);
        this.onMapTargetBubbleLocationChanged(this.subject);

        super.start();

        this.subject.addOnCrosshairColorChangedListener(this);
        this.renderCtx.registerControl(this.subject, this);
    }

    @Override
    public void stop() {
        this.renderCtx.unregisterControl(this.subject, this);
        this.subject.removeOnCrosshairColorChangedListener(this);

        this.subject.removeOnLocationChangedListener(this);
        this.subject.removeOnScaleChangedListener(this);

        super.stop();
    }

    private GLTriangle.Fan _circle;

    /**************************************************************************/

    private final static class GLTargetBubbleCrosshair extends GLAbstractLayer2
            implements CrosshairLayer.OnCrosshairColorChangedListener {
        final static GLLayerSpi2 SPI = new GLLayerSpi2() {
            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public GLLayer2 create(Pair<MapRenderer, Layer> object) {
                if (object.second instanceof CrosshairLayer)
                    return new GLTargetBubbleCrosshair(object.first,
                            (CrosshairLayer) object.second);
                return null;
            }
        };

        GLTriangle.Strip _crossHairLine;
        float colorR;
        float colorG;
        float colorB;
        float colorA;

        GLTargetBubbleCrosshair(MapRenderer surface, CrosshairLayer subject) {
            super(surface, subject, GLMapView.RENDER_PASS_UI);
        }

        @Override
        public void start() {
            super.start();
            ((CrosshairLayer) this.subject)
                    .addOnCrosshairColorChangedListener(this);
            this.onCrosshairColorChanged((CrosshairLayer) this.subject,
                    ((CrosshairLayer) this.subject).getCrosshairColor());
        }

        @Override
        public void stop() {
            ((CrosshairLayer) this.subject)
                    .removeOnCrosshairColorChangedListener(this);
            super.stop();
        }

        @Override
        protected void drawImpl(GLMapView view, int renderPass) {
            if (!MathUtils.hasBits(renderPass, getRenderPass()))
                return;

            if (_crossHairLine == null) {
                _crossHairLine = new GLTriangle.Strip(2, 4);
                _crossHairLine.setX(0, 0f);
                _crossHairLine.setY(0, 0f);

                _crossHairLine.setX(1, 0f);
                _crossHairLine.setY(1, 1f);

                _crossHairLine.setX(2, 1f);
                _crossHairLine.setY(2, 0f);

                _crossHairLine.setX(3, 1f);
                _crossHairLine.setY(3, 1f);
            }

            final float width = view._right - view._left;
            final float height = view._top - view._bottom;

            // XXX - this is a little messy, but we need to capture relative
            //       focus here, as opposed to onAnimate due to baseclass
            //       implementation detail. It is also important to note that
            //       'focusy' is relative to origin at UPPER left, not LOWER
            //       left
            final float fx = view.focusx - view._left;
            final float fy = view.focusy
                    - (((GLMapTargetBubble) view).parent._top - view._top);

            final float pinLength = Math.min(width, height);

            GLES20FixedPipeline.glPushMatrix();

            GLES20FixedPipeline.glColor4f(colorR, colorG, colorB, colorA);

            // the crosshair is centered relative to the focus

            // RIGHT
            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(view._left + fx + pinLength / 8,
                    view._top - fy - 1f, 0f);
            GLES20FixedPipeline.glScalef(width - fx, 3f, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(
                    view._left + fx + pinLength / 32,
                    view._top - fy, 0f);
            GLES20FixedPipeline.glScalef(width - fx, 1, 1f);
            _crossHairLine.draw();

            // LEFT
            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(view._left - pinLength / 8,
                    view._top - fy - 1f, 0f);
            GLES20FixedPipeline.glScalef(fx, 3f, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(view._left - pinLength / 32,
                    view._top - fy, 0f);
            GLES20FixedPipeline.glScalef(fx, 1f, 1f);
            _crossHairLine.draw();

            // TOP
            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(view._left + fx - 1f,
                    view._top - fy + pinLength / 8,
                    0f);
            GLES20FixedPipeline.glScalef(3f, fy, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(view._left + fx,
                    view._top - fy + pinLength / 32,
                    0f);
            GLES20FixedPipeline.glScalef(1f, fy, 1f);
            _crossHairLine.draw();

            // BOTTOM
            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(view._left + fx - 1f,
                    view._bottom - pinLength / 8, 0f);
            GLES20FixedPipeline.glScalef(3f, height - fy, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(view._left + fx,
                    view._bottom - pinLength / 32, 0f);
            GLES20FixedPipeline.glScalef(1f, height - fy, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glPopMatrix();

            // XXX - red center dot
        }

        @Override
        public void release() {
            _crossHairLine = null;
        }

        @Override
        public void onCrosshairColorChanged(CrosshairLayer layer, int color) {
            this.colorR = Color.red(color) / 255f;
            this.colorG = Color.green(color) / 255f;
            this.colorB = Color.blue(color) / 255f;
            this.colorA = Color.alpha(color) / 255f;
        }
    }
}
