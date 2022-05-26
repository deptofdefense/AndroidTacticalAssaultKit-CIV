
package com.atakmap.android.widgets;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.marshal.MarshalManager;
import gov.tak.platform.ui.MotionEvent;

final class WidgetClickForwarder implements IMapWidget.OnClickListener {
    final MapWidget.OnClickListener _impl;

    WidgetClickForwarder(MapWidget.OnClickListener impl) {
        _impl = impl;
    }

    @Override
    public void onMapWidgetClick(IMapWidget widget, MotionEvent event) {
        if (widget instanceof MapWidget)
            _impl.onMapWidgetClick((MapWidget) widget,
                    MarshalManager.marshal(event, MotionEvent.class,
                            android.view.MotionEvent.class));
    }
}
