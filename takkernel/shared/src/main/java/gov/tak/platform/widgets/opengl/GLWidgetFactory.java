
package gov.tak.platform.widgets.opengl;


import java.util.IdentityHashMap;
import java.util.Map;

import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.opengl.IGLWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.utils.ServiceProvider;
import gov.tak.platform.utils.PriorityServiceProviderRegistry;

public final class GLWidgetFactory {

    private final static PriorityServiceProviderRegistry<IGLWidget, SpiArg, SpiImpl> REGISTRY = new PriorityServiceProviderRegistry<>();
    private final static Map<IGLWidgetSpi, SpiImpl> spis = new IdentityHashMap<>();
    private GLWidgetFactory() {
    }

    public static IGLWidget create(MapRenderer view, IMapWidget widget) {
        return REGISTRY
                .create(new SpiArg(view, widget));
    }

    public static void registerSpi(IGLWidgetSpi spi) {
        final SpiImpl toRegister;
        synchronized(spis) {
            if(spis.containsKey(spi))
                return;
            toRegister = new SpiImpl(spi);
            spis.put(spi, toRegister);
        }
        REGISTRY.register(toRegister, spi.getPriority());
    }

    public static void unregisterSpi(IGLWidgetSpi spi) {
        final SpiImpl toUnregister;
        synchronized(spis) {
            toUnregister = spis.remove(spi);
            if(toUnregister == null)
                return;
        }
        REGISTRY.unregister(toUnregister);
    }

    final static class SpiArg {
        final MapRenderer renderer;
        final IMapWidget widget;

        SpiArg(MapRenderer renderer, IMapWidget widget) {
            this.renderer = renderer;
            this.widget = widget;
        }
    }

    final static class SpiImpl implements ServiceProvider<IGLWidget, SpiArg> {

        final IGLWidgetSpi _spi;

        SpiImpl(IGLWidgetSpi spi) {
            _spi = spi;
        }

        @Override
        public IGLWidget create(SpiArg arg) {
            return _spi.create(arg.renderer, arg.widget);
        }
    }
}
