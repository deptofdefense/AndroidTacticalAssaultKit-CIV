
package com.atakmap.android.maps;

import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;

import com.atakmap.annotations.DeprecatedApi;

/**
 * Notification of a specific action that occurred in the mapping engine.
 * 
 * 
 */
public class MapEvent {

    private float _scaleFactor = 1f;
    private MapGroup _group;
    private MapItem _item;
    private PointF _point;
    private final String _type;
    /**
     * This is used for logging, but could be in used in conjunction or to replace "from" in the bundle
     */
    private Class<?> _from;
    private Bundle _extras;

    /**
     * A child MapGroup was added to a parent MapGroup
     */
    public static final String GROUP_ADDED = "group_added";

    /**
     * A child MapGroup was removed from a parent MapGroup
     */
    public static final String GROUP_REMOVED = "group_removed";

    /**
     * 
     */
    public static final String GROUP_REFRESH = "group_refresh";

    /**
     * Signals a refresh of a MapItem to any concerned parties. Usually indicates data from
     * MapItem.getData() has changed and the item needs some sort of refresh to reflect that.
     */
    public static final String ITEM_REFRESH = "item_refresh";

    /**
     * A MapItem is to be saved or peristed to local storage.
     */
    public static final String ITEM_PERSIST = "item_persist";

    /**
     * A MapItem is to be shared to all of the Outputs
     */
    public static final String ITEM_SHARED = "item_shared";

    /**
     * A MapItem was added to a MapGroup
     */
    public static final String ITEM_ADDED = "item_added";

    /**
     * A MapItem was removed from a MapGroup
     */
    public static final String ITEM_REMOVED = "item_removed";

    /**
     * A MapItem was clicked
     */
    public static final String ITEM_CLICK = "item_click";

    /**
     * A MapItem was clicked (confirmed not a double click)
     */
    public static final String ITEM_CONFIRMED_CLICK = "item_confirmed_click";

    /**
     * The map was clicked (confirmed not a double click)
     */
    public static final String MAP_CONFIRMED_CLICK = "map_confirmed_click";

    /**
     * A MapItem was pressed and held (and not released)
     */
    public static final String ITEM_LONG_PRESS = "item_lngpress";

    /**
     * A MapItem was released
     */
    public static final String ITEM_RELEASE = "item_release";

    /**
     * A MapItem was pressed
     */
    public static final String ITEM_PRESS = "item_press";

    /**
     * A MapItem was double clicked
     */
    public static final String ITEM_DOUBLE_TAP = "item_dbltap";

    /**
     * MapItem has changed groups.
     */
    public static final String ITEM_GROUP_CHANGED = "item_group_changed";

    /**
     * MapItem has finished being imported
     * This is different from ITEM_ADDED because some importers add the item to
     * the group before processing details. It also contains helpful extra
     * details such as the "from" tag.
     */
    public static final String ITEM_IMPORTED = "item_imported";

    /**
     * A MapItem was dragged by the user.
     */
    public static final String ITEM_DRAG_STARTED = "item_drgstart";
    public static final String ITEM_DRAG_CONTINUED = "item_drgcont";
    public static final String ITEM_DRAG_DROPPED = "item_drgdrop";

    public static final String MAP_LONG_PRESS = "map_lngpress";
    public static final String MAP_DOUBLE_TAP = "map_dbltap";
    public static final String MAP_PRESS = "map_press";
    public static final String MAP_RELEASE = "map_release";

    /**
     * Animation settled
     */
    public static final String MAP_SETTLED = "map_settled";

    /**
     * The map has been clicked
     */
    public static final String MAP_CLICK = "map_click";

    /**
     * The map has been scrolled directly by input gesture, but has not moved yet
     */
    public static final String MAP_SCROLL = "map_scroll";

    /**
     * Same as scroll except the exact coordinates are passed instead of offset
     */
    public static final String MAP_DRAW = "map_draw";

    /**
     * The map has been scaled directly via input gesture, but has not moved yet
     */
    public static final String MAP_SCALE = "map_scale";

    /**
     * The map has been zoomed via an input alternative to the touch gesture (likely the zoom
     * buttons in the action bar)
     */
    public static final String MAP_ZOOM = "map_zoom";

    /**
     * The map has been moved by either input or controller
     */
    public static final String MAP_MOVED = "map_moved";

    /**
     * The map has been resized, usually caused when a fragment opens or closes.
     */
    public static final String MAP_RESIZED = "map_resized";

    /**
     * The map view is being rotated using the two-finger gesture
     */
    public static final String MAP_ROTATE = "map_rotate";

    /**
     * The map view is being tilted using the two-finger scroll gesture
     */
    public static final String MAP_TILT = "map_tilt";

    /**
     * The map view tilt lock state has been changed
     */
    public static final String MAP_TILT_LOCK = "map_tilt_lock";

    /**
     * The map view rotation lock state has been changed
     */
    public static final String MAP_ROTATE_LOCK = "map_rotate_lock";

    MapEvent(String type) {
        _type = type;
    }

    MapEvent(MapEvent e) {
        _type = e._type;
        _group = e._group;
        _item = e._item;
        _scaleFactor = e._scaleFactor;
        if (e._point != null) {
            _point = new PointF(e._point.x, e._point.y);
        }
        if (e._extras != null) {
            _extras = new Bundle(e._extras);
        }
    }

    /**
     * Returns the type of the MapEvent as a String.
     */
    public String getType() {
        return _type;
    }

    /**
     * Returns the MapItem associated with the MapEvent.   Null if no map item is associated.
     * @return MapItem, null if no map item is associated.
     */
    public MapItem getItem() {
        return _item;
    }

    /**
     * If the MapEvent is associated with a group, return the group that is associated with the event.
     */
    public MapGroup getGroup() {
        return _group;
    }

    /**
     * Return the screen point associated with the event.
     * @return the screen point or null if no screen point is associated.
     * @deprecated use {@link #getPointF()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.3", forRemoval = true, removeAt = "4.6")
    public Point getPoint() {
        // Prevent guaranteed NPE when _point is null
        if (_point == null)
            return null;
        return new Point((int) _point.x, (int) _point.y);
    }

    /**
     * Return the screen point associated with the event.
     * @return the screen point or null if no screen point is associated.
     */
    public PointF getPointF() {
        // Prevent guaranteed NPE when _point is null
        if (_point == null)
            return null;
        return new PointF(_point.x, _point.y);
    }

    public float getScaleFactor() {
        return _scaleFactor;
    }

    public Class<?> getFrom() {
        return _from;
    }

    public Bundle getExtras() {
        return _extras;
    }

    public static class Builder {

        public Builder(String type) {
            _e = new MapEvent(type);
        }

        public Builder setGroup(MapGroup group) {
            _e._group = group;
            return this;
        }

        public MapEvent build() {
            return new MapEvent(_e);
        }

        public Builder setItem(MapItem item) {
            _e._item = item;
            return this;
        }

        public Builder setScaleFactor(float scaleFactor) {
            _e._scaleFactor = scaleFactor;
            return this;
        }

        /** @deprecated use {@link #setPoint(PointF)}*/
        @Deprecated
        @DeprecatedApi(since = "4.3", forRemoval = true, removeAt = "4.6")
        public Builder setPoint(Point p) {
            _e._point = new PointF(p.x, p.y);
            return this;
        }

        public Builder setPoint(PointF p) {
            _e._point = new PointF(p.x, p.y);
            return this;
        }

        public Builder setExtras(Bundle extras) {
            _e._extras = new Bundle(extras);
            return this;
        }

        public Builder setFrom(Class<?> clazz) {
            _e._from = clazz;
            return this;
        }

        private final MapEvent _e;
    }

}
