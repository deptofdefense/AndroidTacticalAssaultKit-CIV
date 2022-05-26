package gov.tak.api.widgets.factory;

import gov.tak.api.widgets.IMapMenuWidgetController;
import gov.tak.api.widgets.IMapWidget;

/**
 * Provider for a IMapMenuWidgetController
 *
 * @param <T> the model type
 */
public interface IMapMenuWidgetControllerSpi<T> extends IWidgetControllerSpi<T> {
    /**
     * Create the controller
     *
     * @param model
     *
     * @return
     */
    IMapMenuWidgetController create(IMapWidget mapMenu, T model);
}
