package com.atakmap.map.opengl;

import com.atakmap.math.MathUtils;

final class LegacyAdapters {
    private LegacyAdapters() {}

    public static GLMapRenderable2 adapt(GLMapRenderable r) {
        if(r instanceof GLMapRenderable2)
            return (GLMapRenderable2)r;
        return new ForwardAdapter(r);
    }

    public static GLMapRenderable adapt(GLMapRenderable2 r) {
        if(r instanceof GLMapRenderable)
            return (GLMapRenderable)r;
        return new BackwardAdapter(r);
    }

    final static class ForwardAdapter implements GLMapRenderable2 {
        final GLMapRenderable impl;

        ForwardAdapter(GLMapRenderable impl) {
            this.impl = impl;
        }
        @Override
        public void draw(GLMapView view, int renderPass) {
            if(!MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE))
                return;
            impl.draw(view);
        }

        @Override
        public void release() {
            impl.release();
        }

        @Override
        public int getRenderPass() {
            return GLMapView.RENDER_PASS_SURFACE;
        }
    }

    final static class BackwardAdapter implements GLMapRenderable {
        final GLMapRenderable2 impl;

        BackwardAdapter(GLMapRenderable2 impl) {
            this.impl = impl;
        }
        @Override
        public void draw(GLMapView view) {
            impl.draw(view, -1);
        }

        @Override
        public void release() {
            impl.release();
        }
    }
}
