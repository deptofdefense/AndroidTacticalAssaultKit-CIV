
package com.atakmap.android.maps.hittest;

import android.graphics.PointF;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapControl;
import com.atakmap.map.hittest.HitTestControl;

/**
 * @deprecated Replaced by {@link HitTestControl}
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public interface ShapeHitTestControl extends MapControl {

    /**
     * The type of hit on the shape
     */
    enum HitType {
        POINT,
        LINE,
        FILL
    }

    /**
     * Result data for {@link #hitTest(float, float, float)}
     */
    class Result {

        // The point on the screen the hit was detected
        public PointF screenPoint;

        // The point on the map the hit was detected
        public GeoPoint geoPoint;

        // The type of hit: POINT, LINE, or FILL
        public HitType hitType;

        // The point or line index hit (-1 for FILL)
        public int hitIndex;
    }

    /**
     * Perform a hit test on a shape
     * @param screenX Screen X
     * @param screenY Screen Y
     * @param radius Screen radius
     * @return Result data (null if not hit)
     */
    Result hitTest(float screenX, float screenY, float radius);
}
