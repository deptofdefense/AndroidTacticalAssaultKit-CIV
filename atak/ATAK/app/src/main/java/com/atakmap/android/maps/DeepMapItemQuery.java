
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;

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
     * @param metadata
     * @return
     */
    MapItem deepFindClosestItem(GeoPoint location, double threshold,
            Map<String, String> metadata);

    Collection<MapItem> deepFindItems(GeoPoint location, double radius,
            Map<String, String> metadata);

    MapItem deepHitTest(int xpos, int ypos, GeoPoint point, MapView view);

    // RC/AML/2015-01-20: Returns sorted set of hit tracks.
    SortedSet<MapItem> deepHitTestItems(int xpos, int ypos,
            GeoPoint point, MapView view);

    Collection<MapItem> deepFindItems(GeoBounds bounds,
            Map<String, String> metadata);

}
