
package gov.tak.api.widgets;


import android.graphics.PointF;

import java.util.List;

public interface IMenuLayoutWidget extends ILayoutWidget {

    // use IMenuLayoutWidgetController.getMapMenuItem instead
    @Deprecated
    Object getMapItem();

    // use IMenuLayoutWidgetController.openMenuOnItem instead
    @Deprecated
    IMapMenuWidget openMenuOnItem(Object item, PointF point);

    void clearMenu();

    // use setMapMenu instead
    @Deprecated
    IMapMenuWidget openMenuOnItem(List<IMapMenuButtonWidget> buttonWidgets, Object item, PointF point);

    /**
     * Set the map menu
     *
     * @param menuWidget
     */
    void setMenuWidget(IMapMenuWidget menuWidget, gov.tak.platform.graphics.PointF point);
}