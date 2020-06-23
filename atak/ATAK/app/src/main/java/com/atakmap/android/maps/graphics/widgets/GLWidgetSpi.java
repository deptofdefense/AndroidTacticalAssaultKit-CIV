
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;

import com.atakmap.android.widgets.MapWidget;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.spi.PriorityServiceProvider;

public interface GLWidgetSpi extends
        PriorityServiceProvider<GLWidget, Pair<MapWidget, GLMapView>> {
}
