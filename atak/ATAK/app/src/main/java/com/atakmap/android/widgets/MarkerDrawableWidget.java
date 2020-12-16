
package com.atakmap.android.widgets;

import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * A {@link DrawableWidget} that can be attached a marker
 */
public class MarkerDrawableWidget extends DrawableWidget {

    private PointMapItem _item;
    private GeoPoint _geoPoint;

    public MarkerDrawableWidget() {
        super();
    }

    public MarkerDrawableWidget(GeoPoint point, Drawable drawable) {
        super(drawable);
        _geoPoint = point;
    }

    public MarkerDrawableWidget(Drawable drawable) {
        this(null, drawable);
    }

    public void setMarker(PointMapItem item) {
        _item = item;
        setGeoPoint(item != null ? item.getPoint() : null);
    }

    public PointMapItem getMarker() {
        return _item;
    }

    public void setGeoPoint(GeoPoint gp) {
        _geoPoint = gp;
        fireChangeListeners();
    }

    public GeoPoint getGeoPoint() {
        return _geoPoint;
    }
}
