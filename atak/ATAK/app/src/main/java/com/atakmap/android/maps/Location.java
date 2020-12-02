
package com.atakmap.android.maps;

import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * Old location interface - has unrelated methods and is missing a bounds method
 * @deprecated Use {@link ILocation} instead
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public interface Location {

    /**
     * It is strongly encouraged that this method provide the full metadata for a point
     * and not just the geopoint wrapped without any metadata.
     * @return the geopoint and metadata that describes this location.
     */
    GeoPointMetaData getLocation();

    /**
     * The friendly name for the object.
     * @return the string name.
     */
    String getFriendlyName();

    /**
     * The corresponding unique identifier.
     * @return the uuid of the object.
     */
    String getUID();

    /**
     * The corresponding map overlay.
     * @return the map overlay for this item.
     */
    MapOverlay getOverlay();
}
