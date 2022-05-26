
package com.atakmap.android.widgets;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.marshal.MarshalManager;
import gov.tak.platform.ui.MotionEvent;

final class WidgetMoveForwarder implements IMapWidget.OnMoveListener {
    final MapWidget.OnMoveListener _impl;

    WidgetMoveForwarder(MapWidget.OnMoveListener impl) {
        _impl = impl;
    }

    @Override
    public boolean onMapWidgetMove(IMapWidget widget, MotionEvent event) {
        if (widget instanceof MapWidget)
            return _impl.onMapWidgetMove((MapWidget) widget,
                    MarshalManager.marshal(event, MotionEvent.class,
                            android.view.MotionEvent.class));
        return false;
    }
}
