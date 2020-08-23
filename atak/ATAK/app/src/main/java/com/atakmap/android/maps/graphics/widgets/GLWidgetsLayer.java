
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;

import com.atakmap.android.widgets.WidgetsLayer;
import com.atakmap.map.LegacyAdapters;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer3;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;

public class GLWidgetsLayer implements GLLayer3 {

    public final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // WidgetsLayer : Layer
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (layer instanceof WidgetsLayer)
                return new GLWidgetsLayer(surface,
                        (WidgetsLayer) layer);
            return null;
        }
    };

    private final MapRenderer renderContext;
    private final WidgetsLayer subject;

    private GLLayoutWidget impl;

    public GLWidgetsLayer(MapRenderer surface, WidgetsLayer layer) {
        this.renderContext = surface;
        this.subject = layer;

        this.impl = null;
    }

    @Override
    public final void draw(GLMapView view) {
        this.draw(view, GLMapView.RENDER_PASS_UI);
    }

    @Override
    public void release() {
        if (impl != null)
            this.impl.releaseWidget();
        this.impl = null;
    }

    @Override
    public Layer getSubject() {
        return this.subject;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_UI;
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_UI)) {
            if (this.impl == null) {
                this.impl = new GLLayoutWidget(this.subject.getRoot(),
                        (GLMapView) this.renderContext);
                this.impl.startObserving(this.subject.getRoot());
                this.impl.orthoView = view;
            }

            GLES20FixedPipeline.glDepthFunc(GLES20FixedPipeline.GL_ALWAYS);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(0,
                    LegacyAdapters.getRenderContext(this.renderContext)
                            .getRenderSurface()
                            .getHeight() - 1,
                    0f);
            this.impl.drawWidget();
            GLES20FixedPipeline.glPopMatrix();
            GLES20FixedPipeline.glDepthFunc(GLES20FixedPipeline.GL_LEQUAL);
        }
    }
}
