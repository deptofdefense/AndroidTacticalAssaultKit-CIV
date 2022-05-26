package gov.tak.platform.binding;

import gov.tak.api.binding.IPropertyBindingObject;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Abstract definition for IPropertyBindingObject
 *
 * @param <T> the top-most exposed binding object class (of this)
 */
public abstract class AbstractPropertyBindingObject<T extends IPropertyBindingObject<T>> implements IPropertyBindingObject<T> {

    private final ConcurrentLinkedQueue<OnPropertyValueChangedListener<T>> _onPropertyChanged = new ConcurrentLinkedQueue<>();

    @Override
    public void setPropertyValue(String propertyName, Object value) {
        Object oldValue = getPropertyValue(propertyName);
        applyPropertyChange(propertyName, value);
        onPropertyValueChanged(propertyName, oldValue, value);
    }

    @Override
    public void onPropertyValueChanged(String propertyName, Object oldValue, Object newValue) {
        for (OnPropertyValueChangedListener<T> l : _onPropertyChanged)
            l.onPropertyValueChanged((T) this,
                    propertyName,
                    oldValue,
                    newValue);
    }

    @Override
    public void addOnPropertyValueChangedListener(OnPropertyValueChangedListener<T> l) {
        _onPropertyChanged.add(l);
    }

    @Override
    public void removeOnPropertyValueChangedListener(OnPropertyValueChangedListener<T> l) {
        _onPropertyChanged.remove(l);
    }

    /**
     * Invoked when the binding object should actually change internal state to reflect
     * the property value change
     *
     * @param propertyName the property name
     * @param newValue the new value of the property
     */
    protected void applyPropertyChange(String propertyName, Object newValue) {
        throw new IllegalArgumentException("property not found");
    }
}
