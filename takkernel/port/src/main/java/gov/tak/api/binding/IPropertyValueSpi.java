package gov.tak.api.binding;

import gov.tak.platform.binding.PropertyInfo;
import gov.tak.platform.binding.PropertyValue;

/**
 * Provider for a property value
 *
 * @param <T> the model type
 */
public interface IPropertyValueSpi<T> {

    /**
     * Create the property value
     *
     * @param propertyInfo the property descriptor
     * @param model the subject to obtain the property value from
     *
     * @return a property value instance or null if no such value exists
     */
    PropertyValue create(PropertyInfo propertyInfo, T model);
}
