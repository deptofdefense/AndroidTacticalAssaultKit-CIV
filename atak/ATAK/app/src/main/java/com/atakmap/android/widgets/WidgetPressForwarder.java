
package com.atakmap.android.widgets;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.marshal.MarshalManager;
import gov.tak.platform.ui.MotionEvent;

public class WidgetPressForwarder implements IMapWidget.OnPressListener {
    final MapWidget.OnPressListener _impl;

    WidgetPressForwarder(MapWidget.OnPressListener impl) {
        _impl = impl;
    }

    @Override
    public void onMapWidgetPress(IMapWidget widget, MotionEvent event) {
        if (widget instanceof MapWidget)
            _impl.onMapWidgetPress((MapWidget) widget,
                    MarshalManager.marshal(event, MotionEvent.class,
                            android.view.MotionEvent.class));
    }
}
