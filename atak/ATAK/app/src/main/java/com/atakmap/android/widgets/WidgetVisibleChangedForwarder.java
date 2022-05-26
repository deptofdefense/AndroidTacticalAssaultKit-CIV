
package com.atakmap.android.widgets;

import gov.tak.api.widgets.IMapWidget;

public class WidgetVisibleChangedForwarder
        implements IMapWidget.OnVisibleChangedListener {
    final MapWidget.OnVisibleChangedListener _impl;

    WidgetVisibleChangedForwarder(MapWidget.OnVisibleChangedListener impl) {
        _impl = impl;
    }

    @Override
    public void onVisibleChanged(IMapWidget widget) {
        if (widget instanceof MapWidget)
            _impl.onVisibleChanged((MapWidget) widget);
    }
}
