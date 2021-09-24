package com.atakmap.map.layer.feature.service;

import java.util.Collection;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapControl;
import com.atakmap.map.hittest.HitTestControl;

/**
 * @deprecated Implement {@link HitTestControl} instead
 */
@Deprecated
@DeprecatedApi(since="4.4", forRemoval = true, removeAt = "4.7")
public interface FeatureHitTestControl extends MapControl {

    /**
     * Performs a hit-test at the specified location. Returns the features at
     * the specified location, inserted in top-most to bottom-most order.
     *
     * @param fids          The FIDs for features at the specified location
     *                      Insertion order is assumed to be top-most Z first.
     * @param screenX       The x-coordinate of the location, in screen pixels
     * @param screenY       The y-coordinate of the location, in screen pixels
     * @param point         The coordinate associated with the location
     * @param resolution    The resolution of the map at the time of the
     *                      selection
     * @param radius        A radius, in pixels, around the specified point that
     *                      may be considered valid for the hit-test
     * @param limit         If non-zero, specifies the maximum number of results
     *                      that may be added to the return value.
     */
    public void hitTest(Collection<Long> fids, float screenX, float screenY, GeoPoint point, double resolution, float radius, int limit);
}
