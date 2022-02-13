package gov.tak.api.binding;

import gov.tak.api.util.Visitor;
import gov.tak.platform.binding.PropertyInfo;

/**
 * Interface for any object that should support property binding
 *
 * @param <T> the interface type of this binding object
 */
public interface IPropertyBindingObject<T extends IPropertyBindingObject<T>> {

    /**
     * Listener for property value changes
     *
     * @param <T> the interface type of this binding object
     */
    interface OnPropertyValueChangedListener<T> {
        /**
         * Called when a biding object's property value changes
         *
         * @param bindingObject
         * @param propertyName
         * @param oldValue
         * @param value
         */
        void onPropertyValueChanged(T bindingObject,
                                    String propertyName,
                                    Object oldValue,
                                    Object value);
    }

    /**
     * Set the value of a property of the Widget
     *
     * @param propertyName
     * @param value
     */
    void setPropertyValue(String propertyName, Object value);

    /**
     * Get the value of a property of the Widget
     *
     * @param property
     * @return
     */
    Object getPropertyValue(String property);

    /**
     * Visit all bindable properties
     *
     * @return
     */
    void visitPropertyInfos(Visitor<PropertyInfo> visitor);

    /**
     * Called when a property value changes
     *
     * @param propertyName
     * @param oldValue
     * @param newValue
     */
    void onPropertyValueChanged(String propertyName,
                                Object oldValue,
                                Object newValue);

    /**
     * Add a property value changed listener
     *
     * @param l
     */
    void addOnPropertyValueChangedListener(OnPropertyValueChangedListener<T> l);

    /**
     * Remove a property value changed listener
     *
     * @param l
     */
    void removeOnPropertyValueChangedListener(OnPropertyValueChangedListener<T> l);
}
