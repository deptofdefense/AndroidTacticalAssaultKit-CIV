package com.atakmap.map;

import com.atakmap.map.opengl.GLMapView;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public final class LegacyAdapters {
    final static Map<WeakReference<MapRenderer>, RenderContext> legacyContextAdapters = new HashMap<>();

    private LegacyAdapters() {}

    public static RenderContext getRenderContext(MapRenderer renderer) {
        if(renderer instanceof MapRenderer2)
            return ((MapRenderer2)renderer).getRenderContext();
        synchronized(legacyContextAdapters) {
            Iterator<Map.Entry<WeakReference<MapRenderer>, RenderContext>> iter = legacyContextAdapters.entrySet().iterator();
            while(iter.hasNext()) {
                final Map.Entry<WeakReference<MapRenderer>, RenderContext> entry = iter.next();
                final MapRenderer r = entry.getKey().get();
                if(r == null) {
                    iter.remove();
                    continue;
                } else if(r == renderer) {
                    return entry.getValue();
                }
            }

            final RenderContext adapted = new MapRendererContext(renderer);
            legacyContextAdapters.put(new WeakReference<MapRenderer>(renderer), adapted);
            return adapted;
        }
    }

    final static class MapRendererContext implements RenderContext {
        final MapRenderer impl;
        final RenderSurface surface;

        MapRendererContext(MapRenderer impl) {
            this.impl = impl;
            if(impl instanceof GLMapView)
                surface = ((GLMapView)impl).getRenderSurface();
            else
                surface = new RenderSurface() {
                    @Override
                    public double getDpi() { return 96d; }
                    @Override
                    public int getWidth() { return 1; }
                    @Override
                    public int getHeight() { return 1; }
                    @Override
                    public void addOnSizeChangedListener(OnSizeChangedListener l) {}
                    @Override
                    public void removeOnSizeChangedListener(OnSizeChangedListener l) {}
                };
        }

        @Override
        public boolean isRenderThread() { return this.impl.isRenderThread(); }
        @Override
        public void queueEvent(Runnable r) { this.impl.queueEvent(r); }
        @Override
        public void requestRefresh() { this.impl.requestRefresh(); }
        @Override
        public void setFrameRate(float rate) { this.impl.setFrameRate(rate); }
        @Override
        public float getFrameRate() { return this.impl.getFrameRate(); }
        @Override
        public void setContinuousRenderEnabled(boolean enabled) { this.impl.setContinuousRenderEnabled(enabled); }
        @Override
        public boolean isContinuousRenderEnabled() { return this.impl.isContinuousRenderEnabled(); }
        @Override
        public boolean supportsChildContext() { return false; }
        @Override
        public RenderContext createChildContext() { return null; }
        @Override
        public void destroyChildContext(RenderContext child) {}
        @Override
        public boolean isAttached() { return this.isRenderThread(); }
        @Override
        public boolean attach() { return isAttached(); }
        @Override
        public boolean detach() { return false; }
        @Override
        public boolean isMainContext() { return true; }
        @Override
        public RenderSurface getRenderSurface() { return surface; }
    }
}
