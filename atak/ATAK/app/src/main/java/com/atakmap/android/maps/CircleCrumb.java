/**
 * 
 */

package com.atakmap.android.maps;

import com.atakmap.android.track.crumb.Crumb;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.UUID;

/**
 * This class is meant to function exactly like its parent class and
 * exists as a class type which will trigger the crumb to be drawn as a circle.
 *
 */
public class CircleCrumb extends Crumb {
    /**
     * @param point
     */
    public CircleCrumb(GeoPoint point) {
        super(point, UUID.randomUUID().toString());
    }

    /**
     * @param point
     * @param color
     */
    public CircleCrumb(GeoPoint point, int color) {
        super(point, String.valueOf(color));
        // TODO Auto-generated constructor stub
    }

    /**
     * @param point
     * @param color
     * @param dir
     */
    public CircleCrumb(GeoPoint point, int color, float dir) {
        super(point, color, dir, "");
        // TODO Auto-generated constructor stub
    }

}
