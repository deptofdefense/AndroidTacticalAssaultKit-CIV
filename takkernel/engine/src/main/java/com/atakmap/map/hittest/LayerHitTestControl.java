package com.atakmap.map.hittest;

import com.atakmap.map.MapRenderer3;

import java.util.Collection;

/**
 * Extension of {@link HitTestControl} that provides a default implementation
 * of the hit-testing loop provided the implementation also provides a list of
 * objects that need to be hit-tested.
 */
public interface LayerHitTestControl extends HitTestControl {

    /**
     * Get the list of objects that should be hit tested
     * Note: This list MUST be thread-safe
     * @return List of objects to hit test
     */
    Collection<?> getHitTestList();

    @Override
    default void hitTest(MapRenderer3 renderer, HitTestQueryParameters params, Collection<HitTestResult> results) {

        // Pull the list of items that should be hit-tested
        Collection<?> objects = getHitTestList();

        // Hit-test items
        for (Object r : objects) {
            if (params.hitLimit(results))
                break;
            if (r instanceof HitTestable) {
                // Hit-test a single item
                HitTestable t = (HitTestable) r;
                HitTestResult result = t.hitTest(renderer, params);
                if (result != null)
                    results.add(result);
            } else if (r instanceof HitTestControl) {
                // Hit-test that may return multiple items
                ((HitTestControl) r).hitTest(renderer, params, results);
            }
        }
    }
}
