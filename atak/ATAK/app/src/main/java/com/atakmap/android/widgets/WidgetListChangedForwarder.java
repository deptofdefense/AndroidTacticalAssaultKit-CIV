
package com.atakmap.android.widgets;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IParentWidget;

public class WidgetListChangedForwarder
        implements IParentWidget.OnWidgetListChangedListener {
    final AbstractParentWidget.OnWidgetListChangedListener _impl;

    WidgetListChangedForwarder(
            AbstractParentWidget.OnWidgetListChangedListener impl) {
        _impl = impl;
    }

    @Override
    public void onWidgetAdded(IParentWidget parent, int index,
            IMapWidget child) {
        if (parent instanceof AbstractParentWidget
                && child instanceof MapWidget)
            _impl.onWidgetAdded((AbstractParentWidget) parent, index,
                    (MapWidget) child);
    }

    @Override
    public void onWidgetRemoved(IParentWidget parent, int index,
            IMapWidget child) {
        if (parent instanceof AbstractParentWidget
                && child instanceof MapWidget)
            _impl.onWidgetRemoved((AbstractParentWidget) parent, index,
                    (MapWidget) child);
    }
}
