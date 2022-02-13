package gov.tak.api.widgets.factory;

import gov.tak.api.widgets.IMapWidget;

/**
 * Provider for a IMapWidget
 *
 * @param <T> the definition type
 */
public interface IMapWidgetSpi<T> {
    /**
     * Handles creating a widget or ignores
     *
     * @param definition the definition object that represents the the widget
     *
     * @return a widget instance if handled, else null
     */
    IMapWidget create(T definition);
}
