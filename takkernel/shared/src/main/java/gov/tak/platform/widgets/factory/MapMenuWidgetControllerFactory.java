package gov.tak.platform.widgets.factory;

import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.api.widgets.IMapMenuWidgetController;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.factory.IMapMenuWidgetControllerSpi;
import gov.tak.api.widgets.factory.IWidgetControllerSpi;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates IMapMenuWidgetControllers
 */
public class MapMenuWidgetControllerFactory {

    private static final WidgetControllerFactory<Object> impl = new WidgetControllerFactory<>();
    private static final Map<Object, IMapMenuWidgetControllerSpi<Object>> matchSpis = new HashMap<>();

    /**
     * Create a controller given a widget and model
     *
     * @param model
     *
     * @return
     */
    public static IMapMenuWidgetController create(IMapMenuWidget widget, Object model) {
        return (IMapMenuWidgetController) impl.create(widget, model);
    }

    /**
     * Register a provider for possible creation of a IMapMenuWidget
     *
     * @param spi the candidate provider
     * @param priority the priority (lower is sooner)
     */
    public static void registerSpi(IMapMenuWidgetControllerSpi<Object> spi, int priority) {
        impl.registerSpi(spi, priority);
    }

    /**
     * Register a provider with a specific definition type for possible creation of a IMapMenuWidget
     * @param spi the candidate provider
     * @param matchClass the class to match on (usually Class<T>)
     * @param priority the priority (lower is sooner)
     * @param <T> the definition type
     */
    public static <T> void registerSpi(IMapMenuWidgetControllerSpi<T> spi, Class<T> matchClass, int priority) {
        IMapMenuWidgetControllerSpi<Object> newRegister = new SpecificSpi<T>(spi, matchClass);
        IMapMenuWidgetControllerSpi<Object> alreadyRegistered = null;
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
    public static void unregisterSpi(IWidgetControllerSpi<Object> spi) {
        impl.unregisterSpi(spi);
    }

    /**
     * Unregister a specific definition type provider
     *
     * @param spi the provider that is already registered
     * @param <T>
     */
    public static <T> void unregisterSpi(IMapMenuWidgetControllerSpi<T> spi) {
        IMapMenuWidgetControllerSpi<Object> alreadyRegistered = null;
        synchronized (matchSpis) {
            alreadyRegistered = matchSpis.remove(spi);
        }
        if (alreadyRegistered != null)
            impl.unregisterSpi(alreadyRegistered);
    }

    private static class SpecificSpi<T> implements IMapMenuWidgetControllerSpi<Object> {

        final IMapMenuWidgetControllerSpi<T> specificSpi;
        final Class<T> matchClass;

        SpecificSpi(IMapMenuWidgetControllerSpi<T> specificSpi, Class<T> matchClass) {
            this.matchClass = matchClass;
            this.specificSpi = specificSpi;
        }

        @Override
        public IMapMenuWidgetController create(IMapWidget mapMenu, Object model) {
            if (this.matchClass.isAssignableFrom(model.getClass()) &&
                    (mapMenu instanceof IMapMenuWidget))
                return specificSpi.create(mapMenu, (T) model);
            return null;
        }
    }

}
