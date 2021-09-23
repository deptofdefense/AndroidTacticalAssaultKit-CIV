
package com.atakmap.android.maps.graphics;

import android.util.Pair;

import com.atakmap.android.maps.MapItem;
import com.atakmap.map.MapRenderer;
import com.atakmap.spi.PriorityServiceProvider;

public interface GLMapItemSpi2 extends
        PriorityServiceProvider<GLMapItem2, Pair<MapRenderer, MapItem>> {
}
