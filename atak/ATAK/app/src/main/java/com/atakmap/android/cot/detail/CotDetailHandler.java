
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.math.MathUtils;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Collections;
import java.util.Set;

/**
 * Converts map item meta data to CoT details and vice versa
 * These methods should be called when converting to and from CoT
 * for a specific detail (usually supplied by map item metadata)
 */
public abstract class CotDetailHandler {

    // Set of detail names this handler supports
    // This set should not be modified once set
    private final Set<String> _detailNames;

    protected CotDetailHandler(Set<String> detailNames) {
        _detailNames = Collections.unmodifiableSet(detailNames);
    }

    protected CotDetailHandler(String detailName) {
        this(Collections.singleton(detailName));
    }

    /**
     * Get the set of detail names this handler supports
     *
     * @return Set of detail names
     */
    public final Set<String> getDetailNames() {
        return _detailNames;
    }

    /**
     * Convert CoT event detail to map item metadata
     *
     * @param item Map item
     * @param event Item's associated CoT event
     * @param detail The detail associated with this handler (read from this)
     *
     * @return {@link ImportResult#SUCCESS} if handled successfully
     * {@link ImportResult#FAILURE} if handled but failed
     * {@link ImportResult#IGNORE} if not handled or N/A
     * {@link ImportResult#DEFERRED} if we should try again later
     */
    public abstract ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail);

    /**
     * Convert map item metadata to a CoT detail
     *
     * @param item Map item to read
     * @param event Item's associated CoT event
     * @param root The CoT event root detail (add to this)
     *
     * @return True if handled, false if not
     */
    public abstract boolean toCotDetail(MapItem item, CotEvent event,
            CotDetail root);

    /**
     * Check if this handler supports this item
     * Used to filter out certain types from being processed by a handler
     *
     * @param item Map item
     * @param event CoT Event
     * @param detail Associated CoT detail
     * @return True if supported
     */
    public boolean isSupported(MapItem item, CotEvent event, CotDetail detail) {
        return true;
    }

    /**
     * Helper method for obtaining a point given a map item
     *
     * @param item Map item
     * @return A single point representing this map item or null if N/A
     */
    protected GeoPointMetaData getPoint(MapItem item) {
        if (item instanceof PointMapItem)
            return ((PointMapItem) item).getGeoPointMetaData();
        if (item instanceof AnchoredMapItem)
            return getPoint(((AnchoredMapItem) item).getAnchorItem());
        if (item instanceof Shape)
            return ((Shape) item).getCenter();
        return null;
    }

    /**
     * Convert a string to a double (exceptions caught with fallback)
     *
     * @param value String value
     * @param defaultVal Default value if conversion fails
     * @return Converted value or default value if conversion failed
     */
    protected double parseDouble(String value, double defaultVal) {
        return MathUtils.parseDouble(value, defaultVal);
    }

    /**
     * Convert a string to an int (exceptions caught with fallback)
     * @param value String value
     * @param defaultVal Default value if conversion fails
     * @return Converted value or default value if conversion failed
     */
    protected int parseInt(String value, int defaultVal) {
        return MathUtils.parseInt(value, defaultVal);
    }

    /**
     * Find a map item by a UID with a given class
     *
     * @param uid Map item UID
     * @param <T> Class type
     * @return Map item of type T or null if not found or not convertible
     */
    @SuppressWarnings("unchecked")
    protected <T extends MapItem> T getMapItem(String uid) {
        if (uid == null)
            return null;

        MapView mapView = MapView.getMapView();
        if (mapView == null)
            return null;

        MapItem item = mapView.getRootGroup().deepFindUID(uid);
        try {
            return (T) item;
        } catch (Exception ignored) {
            return null;
        }
    }
}
