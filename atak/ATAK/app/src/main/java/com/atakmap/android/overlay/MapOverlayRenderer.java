
package com.atakmap.android.overlay;

import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLResolvableMapRenderable;

/**
 * @deprecated Use {@link GLLayerFactory} mechanism via custom
 *             {@link com.atakmap.map.layer.Layer2 Layer2} and {@Link GLLayer2}
 *             implementations
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public final class MapOverlayRenderer extends AbstractLayer {

    static {
        GLLayerFactory.register(new GLLayerSpi2() {
            @Override
            public int getPriority() {
                // XXX - last resort as this method is deprecated
                return -1;
            }

            @Override
            public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
                final MapRenderer surface = arg.first;
                final Layer layer = arg.second;
                if (layer instanceof MapOverlayRenderer)
                    return GLLayerFactory.adapt(((MapOverlayRenderer) layer)
                            .createRenderer());
                return null;
            }
        });
    }

    public enum RenderStackOp {
        ADD,
        PUSH,
        PUSH_AND_RELEASE,
    }

    public final GLMapRenderable renderable;
    public final MapView.RenderStack stack;
    public final int preferredOrder;
    public final RenderStackOp stackOp;

    public MapOverlayRenderer(MapView.RenderStack stack,
            GLMapRenderable renderable) {
        this(stack, renderable, -1, RenderStackOp.ADD);
    }

    public MapOverlayRenderer(MapView.RenderStack stack,
            GLMapRenderable renderable,
            RenderStackOp stackOp) {
        this(stack, renderable, -1, stackOp);
    }

    public MapOverlayRenderer(MapView.RenderStack stack,
            GLMapRenderable renderable,
            int preferredOrder) {
        this(stack, renderable, preferredOrder, RenderStackOp.ADD);
    }

    public MapOverlayRenderer(MapView.RenderStack stack,
            GLMapRenderable renderable,
            int preferredOrder, RenderStackOp stackOp) {
        super("Map Overlay Renderer");

        this.stack = stack;
        this.renderable = renderable;
        this.preferredOrder = preferredOrder;
        if (stackOp == null)
            throw new NullPointerException();
        this.stackOp = stackOp;
    }

    private GLMapOverlayRenderer createRenderer() {
        if (this.renderable instanceof GLResolvableMapRenderable)
            return new GLResolvableMapOverlayRenderer();
        else
            return new GLMapOverlayRenderer();
    }

    /**************************************************************************/

    private class GLMapOverlayRenderer implements GLLayer {

        public GLMapOverlayRenderer() {
        }

        @Override
        public void draw(GLMapView view) {
            MapOverlayRenderer.this.renderable.draw(view);

        }

        @Override
        public void release() {
            MapOverlayRenderer.this.renderable.release();
        }

        @Override
        public Layer getSubject() {
            return MapOverlayRenderer.this;
        }
    }

    private class GLResolvableMapOverlayRenderer extends GLMapOverlayRenderer
            implements GLResolvableMapRenderable {

        @Override
        public State getState() {
            return ((GLResolvableMapRenderable) MapOverlayRenderer.this.renderable)
                    .getState();
        }

        @Override
        public void suspend() {
            ((GLResolvableMapRenderable) MapOverlayRenderer.this.renderable)
                    .suspend();
        }

        @Override
        public void resume() {
            ((GLResolvableMapRenderable) MapOverlayRenderer.this.renderable)
                    .resume();
        }
    }
}
