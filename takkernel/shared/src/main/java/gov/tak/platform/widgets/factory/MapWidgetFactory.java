package gov.tak.platform.widgets.factory;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.factory.IMapWidgetSpi;
import gov.tak.platform.utils.PriorityServiceProviderRegistry;
import gov.tak.platform.utils.ServiceProvider;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A widget factory used in specific factory implementations
 *
 * @param <T> the type of the widget definition (i.e. Xml node, etc.)
 */
public class MapWidgetFactory<T> {

    final static class SpiImpl<T> implements ServiceProvider<IMapWidget, T> {

        final IMapWidgetSpi<T> _spi;

        SpiImpl(IMapWidgetSpi<T> spi) {
            _spi = spi;
        }

        @Override
        public IMapWidget create(T object) {
            return _spi.create(object);
        }
    }

    private final PriorityServiceProviderRegistry<IMapWidget, T, SpiImpl<T>> registry = new PriorityServiceProviderRegistry<>();
    private final Map<IMapWidgetSpi<T>, SpiImpl<T>> spis = new IdentityHashMap<>();

    /**
     * Create the widget from its definition
     *
     * @param definition
     * @return
     */
    public IMapWidget create(T definition) {
        return registry.create(definition);
    }

    /**
     * Add a provider
     *
     * @param spi
     * @param priority
     */
    public void registerSpi(IMapWidgetSpi<T> spi, int priority) {
        final SpiImpl<T> toRegister;
        synchronized(spis) {
            if(spis.containsKey(spi))
                return;
            toRegister = new SpiImpl<>(spi);
            spis.put(spi, toRegister);
        }
        registry.register(new SpiImpl<>(spi), priority);
    }

    /**
     * Remove a provider
     *
     * @param spi
     */
    public void unregisterSpi(IMapWidgetSpi<T> spi) {
        final SpiImpl<T> toUnregister;
        synchronized(spis) {
            toUnregister = spis.remove(spi);
            if(toUnregister == null)
                return;
        }
        registry.unregister(toUnregister);
    }
}
