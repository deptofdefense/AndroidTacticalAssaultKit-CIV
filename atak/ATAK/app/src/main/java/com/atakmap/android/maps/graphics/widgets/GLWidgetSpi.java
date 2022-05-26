
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;

import com.atakmap.android.widgets.MapWidget;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.spi.PriorityServiceProvider;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.api.widgets.opengl.IGLWidgetSpi} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public interface GLWidgetSpi extends
        PriorityServiceProvider<GLWidget, Pair<MapWidget, GLMapView>> {
}
