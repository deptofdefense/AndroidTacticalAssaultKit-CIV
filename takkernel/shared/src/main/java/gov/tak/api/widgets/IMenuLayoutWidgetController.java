package gov.tak.api.widgets;

import gov.tak.platform.graphics.PointF;

/**
 * Controller of a IMenuLayoutWidget
 */
public interface IMenuLayoutWidgetController extends IWidgetController {

    /**
     * Get the menu layout widget
     *
     * @return
     */
    IMenuLayoutWidget getMenuLayoutWidget();

    /**
     * Trigger a menu opening on an item. The IMapMenuWidget is created
     * using MapMenuWidgetFactory. The IMapMenuWidgetController is created using
     * MapMenuWidgetControllerFactory.
     *
     * @param item the item used as the model for the controller and definition of the menu widget
     * @param point where
     *
     * @return the controller instance
     */
    IMapMenuWidgetController openMenuOnItem(Object item, PointF point);
}
