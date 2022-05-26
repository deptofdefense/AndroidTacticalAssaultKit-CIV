package gov.tak.api.engine.map;

import com.atakmap.map.Globe;
import com.atakmap.map.LegacyAdapters;
import com.atakmap.map.opengl.GLMapView;

import java.util.ArrayList;

public final class MapRendererFactory {
    static final ArrayList<IMapRendererSpi> registry = new ArrayList<>();

    private MapRendererFactory() {}

    /**
     * @param parent parent surface instance
     * @param globe the associated globe
     * @param rendererType  The renderer type. If null, the default renderer implementation associated with the surface type will be created.
     */
    public static MapRenderer create(IGlobe globe, Object parent, Class<?> surfaceType, Class<? extends MapRenderer> rendererType) {
        synchronized (registry) {
            for (IMapRendererSpi mapRendererSpi : registry) {
                MapRenderer mapRenderer = mapRendererSpi.create(globe, parent, surfaceType);
                if (mapRenderer != null && ((rendererType == null) || (rendererType.isAssignableFrom(mapRenderer.getClass()))))
                    return mapRenderer;
            }
            return null;
        }
    }

    public static MapRenderer create(IGlobe globe, Object parent, Class<?> surfaceType) {
        return create(globe, parent, surfaceType, null);
    }

    public static void registerSpi(IMapRendererSpi spi) {
        synchronized (registry) {
            registry.add(spi);
        }
    }

    public static void unregisterSpi(IMapRendererSpi spi) {
        synchronized (registry) {
            registry.remove(spi);
        }
    }
}