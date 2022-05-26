package gov.tak.platform.binding;

import gov.tak.api.binding.IPropertyValueSpi;
import java.util.*;

/**
 * Factory for obtaining property values from a model that supports
 * multiple providers.
 *
 * @param <T> the model type the property value is obtained from
 */
public class PropertyValueFactory<T> {

    private final Set<IPropertyValueSpi<T>> registry = new LinkedHashSet<>();

    /**
     * Add a provider
     *
     * @param spi
     */
    public synchronized void registerSpi(IPropertyValueSpi<T> spi) {
        registry.add(spi);
    }

    /**
     * Remove a provider
     *
     * @param spi
     */
    public synchronized void unregisterSpi(IPropertyValueSpi<T> spi) {
        registry.remove(spi);
    }

    /**
     * Obtain a property value given a model and property info
     *
     * @param propertyInfo the property information
     * @param model the model instance
     *
     * @return a PropertyValue instance or null if no value exists
     */
    public synchronized PropertyValue create(PropertyInfo propertyInfo, T model) {
        for (IPropertyValueSpi<T> spi : registry) {
            PropertyValue value = spi.create(propertyInfo, model);
            if (value != null)
                return value;
        }
        return null;
    }

    /**
     * Create a copy of the factory
     *
     * @return
     */
    public synchronized PropertyValueFactory<T> clone() {
        PropertyValueFactory<T> result = new PropertyValueFactory<>();
        result.registry.addAll(this.registry);
        return result;
    }
}
