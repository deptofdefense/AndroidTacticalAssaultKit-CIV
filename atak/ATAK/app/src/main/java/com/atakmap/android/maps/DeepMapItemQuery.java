
package com.atakmap.android.maps;

import com.atakmap.android.maps.hittest.DeepHitTestQuery;
import com.atakmap.android.maps.hittest.MapItemResultFilter;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.hittest.HitTestQueryParameters;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

public interface DeepMapItemQuery {
    MapItem deepFindItem(Map<String, String> metadata);

    List<MapItem> deepFindItems(Map<String, String> metadata);

    /**
     * Finds the closest MapItem to the specified point, that falls within the specified threshold.
     * 
     * @param location The location
     * @param threshold The search threshold, in meters. Only items that are within this distance
     *            from the point are considered. If no threshold is desired, the value
     *            <code>0.0d</code> should be specified.
     * @param metadata Extra query metadata
     * @return Closest map item or null if none found
     */
    MapItem deepFindClosestItem(GeoPoint location, double threshold,
            Map<String, String> metadata);

    /**
     * Find map items within a given area
     *
     * @param location Geodetic location
     * @param radius Radius in meters
     * @param metadata Extra query metadata
     * @return List of found map items or null if none found
     */
    Collection<MapItem> deepFindItems(GeoPoint location, double radius,
            Map<String, String> metadata);

    /**
     * Find map items within given boundaries
     *
     * @param bounds Geodetic boundaries
     * @param metadata Extra query metadata
     * @return List of found map items or null if none found
     */
    Collection<MapItem> deepFindItems(GeoBounds bounds,
            Map<String, String> metadata);

    /**
     * Perform a hit-test for map items
     *
     * @param xpos X-position
     * @param ypos Y-position
     * @param point Ppoint on the map that was touched
     * @param view Map view
     * @return Map item that was hit or null if none hit
     *
     * @deprecated Implement {@link DeepHitTestQuery#deepHitTest(MapView, HitTestQueryParameters)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    default MapItem deepHitTest(int xpos, int ypos, GeoPoint point,
            MapView view) {
        if (this instanceof DeepHitTestQuery) {
            HitTestQueryParameters params = new HitTestQueryParameters(
                    view.getGLSurface(), xpos, ypos,
                    MapRenderer2.DisplayOrigin.UpperLeft);
            params.geo.set(point);
            params.limit = 1;
            params.resultFilter = new MapItemResultFilter();
            SortedSet<MapItem> results = ((DeepHitTestQuery) this)
                    .deepHitTest(view, params);
            return results != null && !results.isEmpty() ? results.first()
                    : null;
        }
        return null;
    }

    /**
     * Perform a hit-test for map items
     *
     * @param xpos X-position
     * @param ypos Y-position
     * @param point Ppoint on the map that was touched
     * @param view Map view
     * @return Map items that were hit or null if none hit
     *
     * @deprecated Implement {@link DeepHitTestQuery#deepHitTest(MapView, HitTestQueryParameters)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    default SortedSet<MapItem> deepHitTestItems(int xpos, int ypos,
            GeoPoint point, MapView view) {
        if (this instanceof DeepHitTestQuery) {
            HitTestQueryParameters params = new HitTestQueryParameters(
                    view.getGLSurface(), xpos, ypos,
                    MapRenderer2.DisplayOrigin.UpperLeft);
            params.geo.set(point);
            params.limit = MapTouchController.MAXITEMS;
            params.resultFilter = new MapItemResultFilter();
            return ((DeepHitTestQuery) this).deepHitTest(view, params);
        }
        return null;
    }
}
