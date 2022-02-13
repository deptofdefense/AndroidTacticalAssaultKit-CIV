package gov.tak.platform.widgets;

import gov.tak.api.widgets.*;
import gov.tak.platform.graphics.PointF;
import gov.tak.platform.widgets.factory.MapMenuWidgetControllerFactory;
import gov.tak.platform.widgets.factory.MapMenuWidgetFactory;

public class MenuLayoutWidgetController implements IMenuLayoutWidgetController {

    private final IMenuLayoutWidget menuLayout;
    private Object mapMenuItem;

    public MenuLayoutWidgetController(IMenuLayoutWidget menuLayout) {
        this.menuLayout = menuLayout;
    }

    @Override
    public IMenuLayoutWidget getMenuLayoutWidget() {
        return this.menuLayout;
    }

    @Override
    public IMapMenuWidgetController openMenuOnItem(Object item, PointF point) {

        IMapMenuWidget mapMenu = MapMenuWidgetFactory.create(item);
        if (mapMenu == null)
            return null;

        IMapMenuWidgetController controller = MapMenuWidgetControllerFactory.create(mapMenu, item);
        if (controller == null)
            return null;

        this.menuLayout.setMenuWidget(mapMenu, point);
        this.mapMenuItem = item;

        return controller;
    }

    @Override
    public IMapWidget getWidget() {
        return this.menuLayout;
    }

    @Override
    public void refreshProperties() {
        // nothing for now
    }

    @Override
    public void refreshProperty(String propertyName) {
        // nothing for now
    }
}
