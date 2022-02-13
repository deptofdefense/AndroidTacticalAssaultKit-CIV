package gov.tak.platform.widgets.factory;

import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.factory.IMapMenuWidgetSpi;
import gov.tak.api.widgets.factory.IMapWidgetSpi;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating IMapMenuWidgets. The MenuLayoutWidget will use this factory
 * for MapLayoutWidget.openMenuOnItem.
 */
public class MapMenuWidgetFactory {

    private static final MapWidgetFactory<Object> impl = new MapWidgetFactory<>();
    private static final Map<Object, IMapWidgetSpi<Object>> matchSpis = new HashMap<>();

    /**
     * Create a widget given a definition
     *
     * @param definition
     *
     * @return
     */
    public static IMapMenuWidget create(Object definition) {
        return (IMapMenuWidget) impl.create(definition);
    }

    /**
     * Register a provider for possible creation of a IMapMenuWidget
     *
     * @param spi the candidate provider
     * @param priority the priority (lower is sooner)
     */
    public static void registerSpi(IMapMenuWidgetSpi<Object> spi, int priority) {
        impl.registerSpi(spi, priority);
    }

    /**
     * Register a provider with a specific definition type for possible creation of a IMapMenuWidget
     * @param spi the candidate provider
     * @param matchClass the class to match on (usually Class<T>)
     * @param priority the priority (lower is sooner)
     * @param <T> the definition type
     */
    public static <T> void registerSpi(IMapMenuWidgetSpi<T> spi, Class<T> matchClass, int priority) {
        IMapWidgetSpi<Object> newRegister = new SpecificSpi<T>(spi, matchClass);
        IMapWidgetSpi<Object> alreadyRegistered = null;
        synchronized (matchSpis) {
            alreadyRegistered = matchSpis.put(spi, newRegister);
        }
        if (alreadyRegistered != null)
            impl.unregisterSpi(alreadyRegistered);
        impl.registerSpi(newRegister, priority);
    }

    /**
     * Unregister a provider
     *
     * @param spi the provider that is already registered
     */
    public static void unregisterSpi(IMapWidgetSpi<Object> spi) {
        impl.unregisterSpi(spi);
    }

    /**
     * Unregister a specific definition type provider
     *
     * @param spi the provider that is already registered
     * @param <T>
     */
    public static <T> void unregisterSpi(IMapMenuWidgetSpi<T> spi) {
        IMapWidgetSpi<Object> alreadyRegistered = null;
        synchronized (matchSpis) {
            alreadyRegistered = matchSpis.remove(spi);
        }
        if (alreadyRegistered != null)
            impl.unregisterSpi(alreadyRegistered);
    }

    private static class SpecificSpi<T> implements IMapWidgetSpi<Object> {

        final IMapWidgetSpi<T> specificSpi;
        final Class<T> matchClass;

        SpecificSpi(IMapWidgetSpi<T> specificSpi, Class<T> matchClass) {
            this.matchClass = matchClass;
            this.specificSpi = specificSpi;
        }

        @Override
        public IMapWidget create(Object definition) {
            if (this.matchClass.isAssignableFrom(definition.getClass()))
                return specificSpi.create((T)definition);
            return null;
        }
    }

}
