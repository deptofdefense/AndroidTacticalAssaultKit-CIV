
package com.atakmap.android.widgets;

import gov.tak.platform.marshal.MarshalManager;
import gov.tak.platform.ui.MotionEvent;

import com.atakmap.annotations.DeprecatedApi;

import java.util.IdentityHashMap;
import java.util.Map;

import gov.tak.api.widgets.IMapWidget;

/**
 * Map widget with view properties such as width, height, margin, and padding
 */
@Deprecated
@DeprecatedApi(since = "4.4")
public class MapWidget2 extends MapWidget {

    private final Map<MapWidget2.OnWidgetSizeChangedListener, IMapWidget.OnWidgetSizeChangedListener> _onSizeChangedForwarders = new IdentityHashMap<>();

    @Override
    public boolean testHit(float x, float y) {
        return x >= 0 && x < _width + _padding[LEFT] + _padding[RIGHT]
                && y >= 0 && y < _height + _padding[TOP] + _padding[BOTTOM];
    }

    public MapWidget seekHit(android.view.MotionEvent event, float x, float y) {
        IMapWidget widget = seekWidgetHit(MarshalManager.marshal(event,
                android.view.MotionEvent.class, MotionEvent.class), x, y);
        if (widget instanceof MapWidget)
            return (MapWidget) widget;
        return null;
    }

    @Override
    public IMapWidget seekWidgetHit(MotionEvent event, float x, float y) {
        MapWidget r = null;
        if (isVisible() && isTouchable() && testHit(x, y))
            r = this;
        return r;
    }

    public MapWidget seekHit(float x, float y) {
        return seekHit(null, x, y);
    }

    public void addOnWidgetSizeChangedListener(
            OnWidgetSizeChangedListener l) {
        final IMapWidget.OnWidgetSizeChangedListener forwarder;
        synchronized (_onSizeChangedForwarders) {
            if (_onSizeChangedForwarders.containsKey(l))
                return;
            forwarder = new SizeChangedForwarder(l);
            _onSizeChangedForwarders.put(l, forwarder);
        }
        super.addOnWidgetSizeChangedListener(forwarder);
    }

    public void removeOnWidgetSizeChangedListener(
            OnWidgetSizeChangedListener l) {
        final IMapWidget.OnWidgetSizeChangedListener forwarder;
        synchronized (_onSizeChangedForwarders) {
            forwarder = _onSizeChangedForwarders.remove(l);
            if (forwarder == null)
                return;
        }
        super.removeOnWidgetSizeChangedListener(forwarder);
    }

    public void onSizeChanged() {
        super.onSizeChanged();
    }

    public interface OnWidgetSizeChangedListener {
        void onWidgetSizeChanged(MapWidget2 widget);
    }

    /**
     * Helper method for retrieving size of a widget with included
     * visibility and class checks
     * @param w Map widget
     * @param incPadding Include padding in size
     * @param incMargin Include margin in size
     * @return [width, height]
     */
    public static float[] getSize(IMapWidget w, boolean incPadding,
            boolean incMargin) {
        if (w != null && w.isVisible() && w instanceof MapWidget2)
            return ((MapWidget2) w).getSize(incPadding, incMargin);
        return new float[] {
                0, 0
        };
    }

    private final class SizeChangedForwarder
            implements IMapWidget.OnWidgetSizeChangedListener {
        final MapWidget2.OnWidgetSizeChangedListener _cb;

        private SizeChangedForwarder(OnWidgetSizeChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onWidgetSizeChanged(IMapWidget widget) {
            if (widget instanceof MapWidget2)
                _cb.onWidgetSizeChanged((MapWidget2) widget);
        }
    }
}
