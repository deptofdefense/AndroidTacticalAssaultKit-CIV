
package com.atakmap.android.widgets;

import gov.tak.api.widgets.IMapWidget;

final class WidgetPointChangedForwarder
        implements IMapWidget.OnWidgetPointChangedListener {
    final MapWidget.OnWidgetPointChangedListener _impl;

    WidgetPointChangedForwarder(MapWidget.OnWidgetPointChangedListener impl) {
        _impl = impl;
    }

    @Override
    public void onWidgetPointChanged(IMapWidget widget) {
        if (widget instanceof MapWidget)
            _impl.onWidgetPointChanged((MapWidget) widget);
    }
}
