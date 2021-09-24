
package com.atakmap.android.maps.hittest;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestControl;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.map.layer.Layer2;
import com.atakmap.util.Visitor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

/**
 * An extension of {@link DeepHitTestQuery} that may utilize a list of
 * {@link HitTestControl} objects which were retrieved earlier in the stack
 */
public interface DeepHitTestControlQuery extends DeepHitTestQuery {

    @Override
    default SortedSet<MapItem> deepHitTest(MapView mapView,
            HitTestQueryParameters params) {
        return deepHitTest(mapView, params, new HashMap<>());
    }

    /**
     * Perform a hit test with access to hit test controls
     *
     * NOTE: This is called on the GL thread with a lock on the map controls
     * Do NOT call {@link MapRenderer3#visitControl(Layer2, Visitor, Class)}
     * or any related method here or else the app will deadlock.
     * This is why the controls are provided upfront.
     *
     * @param mapView Map view
     * @param params Hit test parameters
     * @param controls Hit test controls mapped by layer
     * @return Set of map items that were hit
     */
    SortedSet<MapItem> deepHitTest(MapView mapView,
            HitTestQueryParameters params,
            Map<Layer2, Collection<HitTestControl>> controls);
}
