
package com.atakmap.android.maps.graphics;

import java.util.IdentityHashMap;
import java.util.Map;

import android.util.Pair;

import com.atakmap.android.maps.MapItem;
import com.atakmap.map.MapRenderer;
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
            return this.impl.create(object);
        }
    }
}
