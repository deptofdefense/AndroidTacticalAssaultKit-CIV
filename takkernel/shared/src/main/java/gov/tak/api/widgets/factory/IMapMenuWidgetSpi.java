package gov.tak.api.widgets.factory;

import gov.tak.api.widgets.IMapMenuWidget;

/**
 * Provider of a IMapMenuWidget
 *
 * @param <T> the definition type
 */
public interface IMapMenuWidgetSpi<T> extends IMapWidgetSpi<T> {

    /**
     * Create the widget
     *
     * @param defintion
     *
     * @return
     */
    IMapMenuWidget create(T defintion);
}
