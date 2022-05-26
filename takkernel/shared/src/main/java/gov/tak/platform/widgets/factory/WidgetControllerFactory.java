package gov.tak.platform.widgets.factory;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IWidgetController;
import gov.tak.api.widgets.factory.IWidgetControllerSpi;
import gov.tak.platform.utils.PriorityServiceProviderRegistry;
import gov.tak.platform.utils.ServiceProvider;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A widget controller factory used in specific factory implementations
 *
 * @param <T> the type of the controller model (i.e. ConfigWidgetModel etc.)
 */
public class WidgetControllerFactory<T> {

    private final static class DefWrapper<T> {
        DefWrapper(IMapWidget w, T m) {
            widget = w;
            model = m;
        }
        IMapWidget widget;
        T model;
    }

    final static class SpiImpl<T> implements ServiceProvider<IWidgetController, DefWrapper<T>> {

        final IWidgetControllerSpi<T> _spi;

        SpiImpl(IWidgetControllerSpi<T> spi) {
            _spi = spi;
        }

        @Override
        public IWidgetController create(DefWrapper<T> wrapper) {
            return _spi.create(wrapper.widget, wrapper.model);
        }
    }

    private final PriorityServiceProviderRegistry<IWidgetController, DefWrapper<T>, SpiImpl<T>> registry = new PriorityServiceProviderRegistry<>();
    private final Map<IWidgetControllerSpi<T>, SpiImpl<T>> spis = new IdentityHashMap<>();

    /**
     * Create the widget controller from its model
     *
     * @param widget
     * @param model
     * @return
     */
    public IWidgetController create(IMapWidget widget, T model) {
        return registry.create(new DefWrapper<T>(widget, model));
    }

    /**
     * Add a provider
     *
     * @param spi
     * @param priority
     */
    public void registerSpi(IWidgetControllerSpi<T> spi, int priority) {
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
    public void unregisterSpi(IWidgetControllerSpi<T> spi) {
        final SpiImpl<T> toUnregister;
        synchronized(spis) {
            toUnregister = spis.remove(spi);
            if(toUnregister == null)
                return;
        }
        registry.unregister(toUnregister);
    }
}
