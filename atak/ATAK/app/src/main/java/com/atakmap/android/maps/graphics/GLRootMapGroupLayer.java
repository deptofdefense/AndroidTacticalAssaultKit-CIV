
package com.atakmap.android.maps.graphics;

import android.util.Pair;

import com.atakmap.android.maps.RootMapGroupLayer;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayer3;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapView;

public class GLRootMapGroupLayer implements GLLayer3 {

    public final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // RootMapGroupLayer : Layer
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (layer instanceof RootMapGroupLayer)
                return new GLRootMapGroupLayer(surface,
                        (RootMapGroupLayer) layer);
            return null;
        }
    };

    private final MapRenderer renderContext;
    private final RootMapGroupLayer subject;

    private GLMapGroup2 _rootObserver;
    private final GLQuadtreeNode2 renderer;

    public GLRootMapGroupLayer(MapRenderer surface, RootMapGroupLayer subject) {
        this.renderContext = surface;
        this.subject = subject;
        this.renderer = new GLQuadtreeNode2();
    }

    @Override
    public final int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES | GLMapView.RENDER_PASS_SURFACE;
    }

    @Override
    public final void draw(GLMapView view) {
        this.draw(view, GLMapView.RENDER_PASS_SPRITES
                | GLMapView.RENDER_PASS_SURFACE);
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if (_rootObserver == null) {
            // add the root group observer
            _rootObserver = new GLMapGroup2(this.renderContext, this.renderer,
                    this.subject.getSubject());
            _rootObserver.startObserving(this.subject.getSubject());
        }

        this.renderer.draw(view, renderPass);
    }

    @Override
    public void release() {
        if (_rootObserver != null) {
            _rootObserver.stopObserving(this.subject.getSubject());
            _rootObserver.dispose();
            _rootObserver = null;

            this.renderer.release();
        }
    }

    @Override
    public Layer getSubject() {
        return this.subject;
    }

    @Override
    public void start() {
        renderContext.registerControl(this.subject, this.renderer);
    }

    @Override
    public void stop() {
        renderContext.unregisterControl(this.subject, this.renderer);
    }
}
