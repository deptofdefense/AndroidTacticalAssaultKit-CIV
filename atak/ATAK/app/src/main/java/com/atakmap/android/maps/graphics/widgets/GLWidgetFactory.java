
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;

import com.atakmap.android.widgets.MapWidget;
import com.atakmap.map.LegacyAdapters;
import com.atakmap.map.opengl.GLMapView;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.opengl.IGLWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLWidgetFactory} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public final class GLWidgetFactory {
    private GLWidgetFactory() {
    }

    public static GLWidget create(MapWidget widget, GLMapView view) {
        final IGLWidget result = create2(widget, view);
        if (result == null)
            return null;
        else if (result instanceof GLWidget)
            return (GLWidget) result;

        result.releaseWidget();
        return null;
    }

    public static IGLWidget create2(MapWidget widget, GLMapView view) {
        return gov.tak.platform.widgets.opengl.GLWidgetFactory
                .create(LegacyAdapters.adapt(view), widget);
    }

    public static void registerSpi(GLWidgetSpi spi) {
        gov.tak.platform.widgets.opengl.GLWidgetFactory
                .registerSpi(new SPIAdaptor(spi));
    }

    public static void unregisterSpi(GLWidgetSpi spi) {
        gov.tak.platform.widgets.opengl.GLWidgetFactory
                .unregisterSpi(new SPIAdaptor(spi));
    }

    public static class SPIAdaptor implements IGLWidgetSpi {
        GLWidgetSpi _spi;

        public SPIAdaptor(GLWidgetSpi spi) {
            _spi = spi;
        }

        @Override
        public IGLWidget create(MapRenderer renderer, IMapWidget subject) {
            if (subject instanceof MapWidget) {
                MapWidget widget = (MapWidget) subject;
                GLMapView mapView = LegacyAdapters.adapt(renderer);
                if (mapView != null)
                    return _spi.create(new Pair<>(widget, mapView));
            }

            return null;
        }

        @Override
        public int getPriority() {
            return _spi.getPriority();
        }
    }

}
