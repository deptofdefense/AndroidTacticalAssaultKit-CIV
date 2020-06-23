
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;

import com.atakmap.android.widgets.MapWidget;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.spi.PriorityServiceProviderRegistry2;

public final class GLWidgetFactory {

    private final static PriorityServiceProviderRegistry2<GLWidget, Pair<MapWidget, GLMapView>, GLWidgetSpi> REGISTRY = new PriorityServiceProviderRegistry2<>();

    private GLWidgetFactory() {
    }

    public static GLWidget create(MapWidget widget, GLMapView view) {
        return REGISTRY
                .create(Pair.create(widget, view));
    }

    public static void registerSpi(GLWidgetSpi spi) {
        REGISTRY.register(spi, spi.getPriority());
    }

    public static void unregisterSpi(GLWidgetSpi spi) {
        REGISTRY.unregister(spi);
    }
}
