
package com.atakmap.android.toolbars;

import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * @deprecated Use {@link CotEventFactory#createCotEvent(MapItem)} instead
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.6")
public class RangeAndBearingCotEventSpi {

    private final MapView mapView;

    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.6")
    public RangeAndBearingCotEventSpi(MapView mapView) {
        this.mapView = mapView;
    }

    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.6")
    public CotEvent createCotEvent(MapItem item) {
        return createCotEvent(this.mapView, item);
    }

    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.6")
    public static CotEvent createCotEvent(MapView mapView, MapItem item) {
        if (item instanceof RangeAndBearingMapItem)
            return CotEventFactory.createCotEvent(item);
        else
            return null;
    }
}
