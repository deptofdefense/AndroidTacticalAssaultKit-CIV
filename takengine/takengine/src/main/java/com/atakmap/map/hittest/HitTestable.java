package com.atakmap.map.hittest;

import com.atakmap.map.MapRenderer3;

/**
 * Interface used for renderables that can be hit-tested
 */
public interface HitTestable {

    /**
     * Interface for performing a hit test on an object
     * Note: This is always called on the GL thread
     *
     * @param renderer Map renderer
     * @param params Query parameters
     * @return Result if the item has been hit, null otherwise
     */
    HitTestResult hitTest(MapRenderer3 renderer, HitTestQueryParameters params);
}
