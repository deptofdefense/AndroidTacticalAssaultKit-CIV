
package com.atakmap.android.widgets;

import gov.tak.api.widgets.IMapWidget;

public class WidgetLongPressForwarder
        implements IMapWidget.OnLongPressListener {
    final MapWidget.OnLongPressListener _impl;

    WidgetLongPressForwarder(MapWidget.OnLongPressListener impl) {
        _impl = impl;
    }

    @Override
    public void onMapWidgetLongPress(IMapWidget widget) {
        if (widget instanceof MapWidget)
            _impl.onMapWidgetLongPress((MapWidget) widget);
    }
}
