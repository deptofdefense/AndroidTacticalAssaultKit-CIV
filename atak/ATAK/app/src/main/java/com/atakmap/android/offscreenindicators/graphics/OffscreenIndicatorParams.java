
package com.atakmap.android.offscreenindicators.graphics;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;

import java.util.SortedSet;
import java.util.TreeSet;

class OffscreenIndicatorParams {

    final SortedSet<Marker> markers = new TreeSet<>(
            MapItem.ZORDER_RENDER_COMPARATOR);
}
