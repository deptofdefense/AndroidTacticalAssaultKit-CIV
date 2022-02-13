package com.atakmap.map.layer.model;


import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapControl;

public interface ModelHitTestControl extends MapControl {
    /**
     * Performs a hit-test against
     * @param screenX
     * @param screenY
     * @param result
     * @return
     */
    public boolean hitTest(float screenX, float screenY, GeoPoint result);
}
