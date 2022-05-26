
package com.atakmap.android.widgets;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.marshal.MarshalManager;
import gov.tak.platform.ui.MotionEvent;

final class WidgetUnpressForwarder implements IMapWidget.OnUnpressListener {
    final MapWidget.OnUnpressListener _impl;

    WidgetUnpressForwarder(MapWidget.OnUnpressListener impl) {
        _impl = impl;
    }

    @Override
    public void onMapWidgetUnpress(IMapWidget widget, MotionEvent event) {
        if (widget instanceof MapWidget)
            _impl.onMapWidgetUnpress((MapWidget) widget,
                    MarshalManager.marshal(event, MotionEvent.class,
                            android.view.MotionEvent.class));
    }
}
