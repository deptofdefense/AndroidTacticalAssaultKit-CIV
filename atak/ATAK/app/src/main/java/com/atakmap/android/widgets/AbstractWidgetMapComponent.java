
package com.atakmap.android.widgets;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

public abstract class AbstractWidgetMapComponent extends AbstractMapComponent {

    public static final String ROOT_LAYOUT_EXTRA = "rootLayoutWidget";

    private WidgetsLayer layer;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        if (view.getComponentExtra(ROOT_LAYOUT_EXTRA) == null) {
            this.layer = new WidgetsLayer("Map Widgets",
                    new RootLayoutWidget(view));
            view.addLayer(MapView.RenderStack.WIDGETS, layer);
            view.setComponentExtra(ROOT_LAYOUT_EXTRA, layer.getRoot());
        }
        // _rootLayoutWidget = (LayoutWidget)view.getComponentExtra(ROOT_LAYOUT_EXTRA);
        LayoutWidget rootLayoutWidget = (LayoutWidget) view
                .getComponentExtra(ROOT_LAYOUT_EXTRA);
        _rootLayoutWidget = new LayoutWidget();
        _rootLayoutWidget.setName("Component Root");
        rootLayoutWidget.addWidget(_rootLayoutWidget);
        onCreateWidgets(context, intent, view);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        onDestroyWidgets(context, view);
        if (this.layer != null)
            view.removeLayer(MapView.RenderStack.WIDGETS, layer);
    }

    protected abstract void onCreateWidgets(Context context, Intent intent,
            MapView view);

    protected abstract void onDestroyWidgets(Context context, MapView view);

    public LayoutWidget getRootLayoutWidget() {
        return _rootLayoutWidget;
    }

    private LayoutWidget _rootLayoutWidget;
}
