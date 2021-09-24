
package com.atakmap.android.maps.hittest;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.hittest.HitTestQueryParameters;

import java.util.SortedSet;

/**
 * Interface for performing hit tests on map items
 */
public interface DeepHitTestQuery {

    /**
     * Perform a hit-test given a set of query parameters
     * NOTE: This is called on the GL thread
     *
     * @param mapView Map view instance
     * @param params Hit-test query parameters (location of hit, bounds, etc.)
     * @return Hit map items or null if none hit
     */
    SortedSet<MapItem> deepHitTest(MapView mapView,
            HitTestQueryParameters params);
}
