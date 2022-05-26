package gov.tak.api.widgets.factory;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IWidgetController;

/**
 * Base interface for factory provider
 */
public interface IWidgetControllerSpi<T> {
    /**
     *
     * @param definition
     *
     * @return
     */
    IWidgetController create(IMapWidget widget, T definition);
}
