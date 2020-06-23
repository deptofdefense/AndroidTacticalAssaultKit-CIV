
package com.atakmap.android.maps.graphics;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.util.Pair;

import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapBatchable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLRenderBatch;
import com.atakmap.spi.PriorityServiceProviderRegistry2;

public final class GLMapItemFactory {

    private final static Map<GLMapItemSpi2, GLMapItemSpi3> spiAdapters = new IdentityHashMap<>();
    private final static PriorityServiceProviderRegistry2<GLMapItem2, Pair<MapRenderer, MapItem>, GLMapItemSpi3> registry = new PriorityServiceProviderRegistry2<>();

    private GLMapItemFactory() {
    }

    public static void registerSpi(GLMapItemSpi2 spi) {
        synchronized (spiAdapters) {
            if (spiAdapters.containsKey(spi))
                return;
            GLMapItemSpi3 adapted = new SpiAdapter(spi);
            spiAdapters.put(spi, adapted);
            registerSpi(adapted);
        }
    }

    public static void registerSpi(GLMapItemSpi3 spi) {
        registry.register(spi, spi.getPriority());
    }

    public static void unregisterSpi(GLMapItemSpi2 spi) {
        synchronized (spiAdapters) {
            final GLMapItemSpi3 adapted = spiAdapters.remove(spi);
            if (adapted == null)
                return;
            unregisterSpi(adapted);
        }
    }

    public static void unregisterSpi(GLMapItemSpi3 spi) {
        registry.unregister(spi);
    }

    /**
     * @deprecated  always returns <code>null</code>; use
     *              {@link #create3(MapRenderer, MapItem)}
     */
    public static GLMapItem create2(MapRenderer surface, MapItem item) {
        return null;
    }

    public static GLMapItem2 create3(MapRenderer surface, MapItem item) {
        return registry.create(Pair.create(surface, item));
    }

    private final static class SpiAdapter implements GLMapItemSpi3 {
        private final GLMapItemSpi2 impl;

        public SpiAdapter(GLMapItemSpi2 impl) {
            this.impl = impl;
        }

        @Override
        public int getPriority() {
            return this.impl.getPriority();
        }

        @Override
        public GLMapItem2 create(Pair<MapRenderer, MapItem> object) {
            GLMapItem retval = this.impl.create(object);
            if (retval == null)
                return null;
            if (retval instanceof GLMapBatchable)
                return new BatchableAdapter(retval);
            else
                return new Adapter(retval);
        }

    }

    private static class Adapter implements GLMapItem2,
            GLMapItem.OnBoundsChangedListener,
            GLMapItem.OnVisibleChangedListener {

        protected final GLMapItem impl;
        private final Collection<GLMapItem2.OnBoundsChangedListener> boundsListeners;
        private final Collection<GLMapItem2.OnVisibleChangedListener> visibleListeners;

        public Adapter(GLMapItem impl) {
            this.impl = impl;
            this.boundsListeners = new ConcurrentLinkedQueue<>();
            this.visibleListeners = new ConcurrentLinkedQueue<>();

            this.impl.addBoundsListener(this);
            this.impl.addVisibleListener(this);
        }

        @Override
        public final MapItem getSubject() {
            return this.impl.getSubject();
        }

        @Override
        public final void setOpaque(Object opaque) {
            this.impl.setOpaque(opaque);
        }

        @Override
        public final Object getOpaque() {
            return this.impl.getOpaque();
        }

        @Override
        public final void startObserving() {
            this.impl.startObserving();
        }

        @Override
        public final void stopObserving() {
            this.impl.stopObserving();
        }

        @Override
        public final int getRenderPass() {
            return GLMapView.RENDER_PASS_SURFACE;
        }

        @Override
        public final void draw(GLMapView view, int renderPass) {
            if ((renderPass & GLMapView.RENDER_PASS_SURFACE) != 0)
                this.impl.draw(view);
        }

        @Override
        public final void release() {
            this.impl.release();
        }

        @Override
        public final boolean isVisible() {
            return this.impl.visible;
        }

        @Override
        public final double getZOrder() {
            return this.impl.zOrder;
        }

        @Override
        public final double getMinDrawResolution() {
            return this.impl.minMapGsd;
        }

        @Override
        public final void addBoundsListener(OnBoundsChangedListener l) {
            this.boundsListeners.add(l);
        }

        @Override
        public final void removeBoundsListener(OnBoundsChangedListener l) {
            this.boundsListeners.remove(l);
        }

        @Override
        public final void addVisibleListener(OnVisibleChangedListener l) {
            this.visibleListeners.add(l);
        }

        @Override
        public final void removeVisibleListener(OnVisibleChangedListener l) {
            this.visibleListeners.remove(l);
        }

        @Override
        public final void getBounds(MutableGeoBounds bnds) {
            bnds.set(this.impl.getBounds());
        }

        @Override
        public final void onVisibleChanged(GLMapItem item) {
            final boolean visible = item.visible;
            for (GLMapItem2.OnVisibleChangedListener l : this.visibleListeners)
                l.onVisibleChanged(this, visible);
        }

        @Override
        public final void onBoundsChanged(GLMapItem item) {
            final GeoBounds bnds = item.getBounds();
            for (GLMapItem2.OnBoundsChangedListener l : this.boundsListeners)
                l.onBoundsChanged(this, bnds);
        }
    }

    private static class BatchableAdapter extends Adapter implements
            GLMapBatchable {

        private final GLMapBatchable impl;

        public BatchableAdapter(GLMapItem impl) {
            super(impl);
            this.impl = (GLMapBatchable) impl;
        }

        @Override
        public boolean isBatchable(GLMapView view) {
            return this.impl.isBatchable(view);
        }

        @Override
        public void batch(GLMapView view, GLRenderBatch batch) {
            this.impl.batch(view, batch);
        }
    }
}
