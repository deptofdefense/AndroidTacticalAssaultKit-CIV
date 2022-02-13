package gov.tak.platform.widgets;

import gov.tak.api.binding.IPropertyValueSpi;
import gov.tak.api.util.Visitor;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IWidgetController;
import gov.tak.platform.binding.PropertyInfo;
import gov.tak.platform.binding.PropertyValue;
import gov.tak.platform.widgets.config.ConfigWidgetModel;

/**
 * Abstract base for a widget controller with a specific model instance and property listening
 * capabilities
 *
 * @param <T> the model type in the Model, View, Controller design pattern
 */
public abstract class AbstractWidgetController<T> implements IWidgetController, IMapWidget.OnPropertyValueChangedListener<IMapWidget> {

    protected IMapWidget widget;
    private T model;

    /**
     * Create the abstract widget controller base
     *
     * @param widget the widget that is controlled (i.e. View in Model, View, Controller design pattern)
     * @param model the model in Model, View, Controller design pattern
     */
    protected AbstractWidgetController(IMapWidget widget, T model) {
        this.widget = widget;
        this.model = model;
    }

    public T getModel() {
        return this.model;
    }

    @Override
    public IMapWidget getWidget() {
        return this.widget;
    }

    @Override
    public void refreshProperties() {
        widget.visitPropertyInfos(new Visitor<PropertyInfo>() {
            @Override
            public void visit(PropertyInfo propertyInfo) {
                PropertyValue propertyValue = resolvePropertyValue(propertyInfo, model);
                if (propertyValue != null)
                    widget.setPropertyValue(propertyInfo.getName(), propertyValue.getValue());
            }
        });
    }

    @Override
    public void refreshProperty(String propertyName) {
        PropertyInfo propertyInfo = findPropertyInfo(propertyName);
        if (propertyInfo != null) {
            PropertyValue propertyValue = resolvePropertyValue(propertyInfo, model);
            if (propertyValue != null)
                widget.setPropertyValue(propertyInfo.getName(), propertyValue.getValue());
        }
    }

    private PropertyInfo findPropertyInfo(String propertyName) {
        final PropertyInfo[] result = {null};
        widget.visitPropertyInfos(new Visitor<PropertyInfo>() {
            @Override
            public void visit(PropertyInfo propertyInfo) {
                if (propertyInfo.equals(propertyName)) {
                    result[0] = propertyInfo;
                }
            }
        });
        return result[0];
    }

    public void startListening() {
        this.stopListening();
        widget.addOnPropertyValueChangedListener(this);
    }

    public void stopListening() {
        widget.removeOnPropertyValueChangedListener(this);
    }

    /**
     * Called when a property value must be obtained from the model instance
     *
     * @param propertyInfo the property info that describes the value
     * @param model the model containing the value
     *
     * @return a PropertyValue instance or null if no such value exists
     */
    protected abstract PropertyValue resolvePropertyValue(PropertyInfo propertyInfo, T model);
}
