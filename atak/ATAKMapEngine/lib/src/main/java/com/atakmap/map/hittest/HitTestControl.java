package com.atakmap.map.hittest;

import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer3;

import java.util.Collection;

/**
 * Map control for hit testing renderables
 *
 * For a helpful default implementation see {@link LayerHitTestControl}
 */
public interface HitTestControl extends MapControl {

    /**
     * Interface for performing a hit test on a group of map items
     * Note: This is always called on the GL thread
     *
     * @param renderer GL instance of the map view
     * @param params Query parameters
     * @param results Results where hit renderables are stored
     */
    void hitTest(MapRenderer3 renderer, HitTestQueryParameters params, Collection<HitTestResult> results);
}
