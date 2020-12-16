
package com.atakmap.android.maps;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import com.atakmap.android.data.URIHelper;
import com.atakmap.android.hashtags.HashtagManager;
import com.atakmap.android.hashtags.util.HashtagSet;
import com.atakmap.android.hashtags.util.HashtagUtils;
import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.maps.visibility.VisibilityCondition;
import com.atakmap.android.maps.visibility.VisibilityListener;
import com.atakmap.android.maps.visibility.VisibilityUtil;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract base for a map engine entity. All MapItems are observable by setting listeners for value
 * changes on members called 'properties'. Each property is individually observable, but many of the
 * core engine components set listeners for every property to properly implement functionality.
 * Generally, client code will not add listeners, but MapComponents will. Listeners should be
 * removed by the same classes that add them.
 * <p>
 * MapItem has the following properties:
 * <li><b>clickable</b> affects touch interaction</li>
 * <li><b>visible</b> affects draw visibility and touch interaction</li>
 * <li><b>zOrder</b> affects draw order (lower appears above higher)</li>
 * </p>
 * <p>
 * Notable <i>UserControlComponent</i> (touch) behavior:
 * <li><b>clickable</b> set as <i>false</i> will cause the item to be skipped when handling touch
 * events</li>
 * <li><b>visible</b> set as <i>false</i> will cause the item to be skipped when handling touch
 * events</li>
 * <li>Lower <b>zOrder</b> values will be hit-tested first possibly causing propagation to skip higher
 * <b>zOrder</b> items</li>
 * </p>
 * <p>
 * Notable <i>GLMapComponent</i> (graphics) behavior:
 * <li>When <b>visible</b> is <i>false</i>, no element of the MapItem is drawn</li>
 * <li>A lower <b>zOrder</b> will be drawn over a higher <b>zOrder</b></li>
 * </p>
 *
 *
 */
public abstract class MapItem extends FilterMetaDataHolder implements
        HashtagContent, VisibilityListener {

    private static final String TAG = "MapItem";

    private final static AtomicLong serialIdGenerator = new AtomicLong(0L);

    /**
     * Orders by Z-order, DESCENDING.  This is an appropriate ordering for
     * purposes of rendering: low Z-order MapItems are rendered after (atop)
     * high Z-order MapItems.  When Z-order values are the same, lower serial ID
     * appears first in sort order (rendering more recently created MapItems
     * atop earlier MapItems).
     */
    public final static Comparator<MapItem> ZORDER_RENDER_COMPARATOR = new Comparator<MapItem>() {
        @Override
        public int compare(MapItem item0, MapItem item1) {
            final double z0 = item0.getZOrder();
            final double z1 = item1.getZOrder();
            if (z0 < z1)
                return 1;
            else if (z0 > z1)
                return -1;
            final long id0 = item0.getSerialId();
            final long id1 = item1.getSerialId();
            if (id0 < id1)
                return -1;
            else if (id0 > id1)
                return 1;
            else
                return 0;
        }
    };

    /**
     * Orders by Z-order, ASCENDING.  This is an appropriate ordering for
     * purposes of hit-testing.  It is the reverse of the order produced by the
     * ZORDER_RENDER_COMPARATOR.
     */
    public final static Comparator<MapItem> ZORDER_HITTEST_COMPARATOR = new Comparator<MapItem>() {
        @Override
        public int compare(MapItem item0, MapItem item1) {
            return ZORDER_RENDER_COMPARATOR.compare(item1, item0);
        }
    };

    /**
     * Default value of the visible property
     */
    public static final boolean VISIBLE_DEFAULT = true;

    /**
     * Default value of the clickable property
     */
    public static final boolean CLICKABLE_DEFAULT = false;

    /**
     * Default value of the zOrder property
     */
    public static final double ZORDER_DEFAULT = 1d;

    /**
     * The CoT type designating an empty type within the system.
     * (unknown - other)
     */
    public static final String EMPTY_TYPE = "no-defined-type";

    /**
     * Default hit radius ratio (32dp)
     */
    public static final double HIT_RATIO_DEFAULT = 32d / 240d;

    /**
     * Visible property listener
     */
    public interface OnVisibleChangedListener {
        void onVisibleChanged(MapItem item);
    }

    /**
     * Type property listener
     */
    public interface OnTypeChangedListener {
        void onTypeChanged(MapItem item);
    }

    public interface OnMetadataChangedListener {
        void onMetadataChanged(MapItem item, final String field);
    }

    /**
     * Clickable property listener
     */
    public interface OnClickableChangedListener {
        void onClickableChanged(MapItem item);
    }

    /**
     * ZOrder property listener
     */
    public interface OnZOrderChangedListener {
        void onZOrderChanged(MapItem item);
    }

    /**
     * Height property listener
     */
    public interface OnHeightChangedListener {
        void onHeightChanged(MapItem item);
    }

    public interface OnGroupChangedListener {
        void onItemAdded(MapItem item, MapGroup group);

        void onItemRemoved(MapItem item, MapGroup group);
    }

    private final long _globalId;
    private final String _uid;
    private Boolean _camLocked = false;
    private String _type = EMPTY_TYPE;
    private final HashtagSet _hashtags = new HashtagSet();

    private Object _tag;
    private boolean _clickable = CLICKABLE_DEFAULT;
    private boolean _visible = VISIBLE_DEFAULT;
    private int _visCond = VisibilityCondition.IGNORE;
    private double _zOrder = ZORDER_DEFAULT;
    private final ConcurrentLinkedQueue<OnVisibleChangedListener> _visibleListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnTypeChangedListener> _typeListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnClickableChangedListener> _clickableListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnZOrderChangedListener> _zOrderListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnGroupChangedListener> _onGroupListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnMetadataChangedListener> _onMetadataChangedListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnHeightChangedListener> _onHeightChanged = new ConcurrentLinkedQueue<>();
    private MapGroup group;

    MapItem(final long serialId, final String uid) {
        this(serialId, new DefaultMetaDataHolder(), uid);
    }

    MapItem(long serialId, MetaDataHolder metadata, final String uid) {
        super(metadata);
        _globalId = serialId;

        if (uid == null || uid.length() == 0) {
            Log.e(TAG, "### ERROR ### UID cannot be nothing", new Exception());
            _uid = UUID.randomUUID().toString();
        } else {
            _uid = uid;
        }

        super.setMetaString("uid", _uid);
    }

    /**
     * Returns the permanent uid for this  MapItem.
     */
    final public String getUID() {
        return _uid;
    }

    /**
     * Set the type of the Marker.
     */
    final public void setType(final String type) {

        if (type != null && !type.equals(_type)) {
            _type = type;
            super.setMetaString("type", type);
            onTypeChanged();
        }

    }

    /**
     * Get the type of the Marker.
     * @return returns the type or MenuItem.EMPTY_TYPE if no type is defined.
     */
    final public String getType() {
        return _type;
    }

    /**
     * Get the title of this map item (display name)
     *
     * @return Item title or null if N/A
     */
    @Override
    public String getTitle() {
        return getMetaString("title", null);
    }

    /**
     * Set the title of this map item (display name)
     *
     * @param title Item title
     */
    public void setTitle(String title) {
        setMetaString("title", title);
    }

    /**
     * Default string conversion which contains the UID, type, and title
     *
     * @return Title, type, UID
     */
    @Override
    public String toString() {
        String title = getTitle();
        if (FileSystemUtils.isEmpty(title))
            title = "[Untitled Item]";
        return title + ", " + getType() + ", " + getUID();
    }

    /**
     * uid and type are very popular calls.
     */
    @Override
    public String getMetaString(final String k, final String dv) {
        switch (k) {
            case "uid":
                return _uid;
            case "type":
                return _type;
            default:
                return super.getMetaString(k, dv);
        }
    }

    /**
     *
     */
    @Override
    public void setMetaString(final String k, final String dv) {
        if (k.equals("uid")) {
            Log.w(TAG, "### WARNING ###  changing an imutable UID --- BLOCKED "
                    + _uid + " to: " + dv);
        } else if (k.equals("type")) {
            setType(dv);
            return;
        } else if (k.equals(getRemarksKey())) {
            setRemarks(dv);
            return;
        }

        super.setMetaString(k, dv);
    }

    @Override
    public boolean getMetaBoolean(final String k, final boolean dv) {
        if (k.equals("camLocked"))
            if (_camLocked != null)
                return _camLocked;
            else
                return dv;
        else
            return super.getMetaBoolean(k, dv);
    }

    @Override
    public void setMetaBoolean(final String k, final boolean dv) {
        if (k.equals("camLocked"))
            _camLocked = dv;

        super.setMetaBoolean(k, dv);
    }

    @Override
    public void removeMetaData(String k) {
        if (k.equals("camLocked"))
            _camLocked = null;
        super.removeMetaData(k);
    }

    /**
     * Add a clickable property listener
     *
     * @param listener the listener to add
     */
    public void addOnClickableChangedListener(
            OnClickableChangedListener listener) {
        _clickableListeners.add(listener);
    }

    /**
     * Add a visible property listener
     *
     * @param listener the listener to add
     */
    public void addOnVisibleChangedListener(OnVisibleChangedListener listener) {
        _visibleListeners.add(listener);
    }

    /**
     * Add a type property listener
     *
     * @param listener the listener to add
     */
    public void addOnTypeChangedListener(OnTypeChangedListener listener) {
        _typeListeners.add(listener);
    }

    /**
     * Remove a clickable property listener
     *
     * @param listener the listener to add
     */
    public void removeOnClickableChangedListener(
            OnClickableChangedListener listener) {
        _clickableListeners.remove(listener);
    }

    /**
     * Remove a visible property listener
     *
     * @param listener the listener to remove
     */
    public void removeOnVisibleChangedListener(
            OnVisibleChangedListener listener) {
        _visibleListeners.remove(listener);
    }

    /**
     * Remove a visible property listener
     *
     * @param listener the listener to remove
     */
    public void removeOnTypeChangedListener(OnTypeChangedListener listener) {
        _typeListeners.remove(listener);
    }

    /**
     * Add a zOrder property listener
     *
     * @param listener the listener to add
     */
    public void addOnZOrderChangedListener(OnZOrderChangedListener listener) {
        _zOrderListeners.add(listener);
    }

    /**
     * Remove a zOrder property listener
     *
     * @param listener the listener to add
     */
    public void removeOnZOrderChangedListener(
            OnZOrderChangedListener listener) {
        _zOrderListeners.remove(listener);
    }

    /**
     * Add a metadata changed property listener
     *
     * @param listener the listener
     */
    public void addOnMetadataChangedListener(
            OnMetadataChangedListener listener) {
        _onMetadataChangedListeners.add(listener);
    }

    /**
     * Remove a metadata property listener
     *
     * @param listener the listener
     */
    public void removeOnMetadataChangedListener(
            OnMetadataChangedListener listener) {
        _onMetadataChangedListeners.remove(listener);
    }

    /**
     * Set the clickable property value. Clickable affects whether the MapItem is hit tested.
     *
     * @param clickable
     */
    public void setClickable(boolean clickable) {
        if (clickable != _clickable) {
            _clickable = clickable;
            onClickableChanged();
        }
    }

    /**
     * Get the clickable property value. Clickable affects whether the MapItem is hit tested.
     *
     * @return true if clickable
     */
    public boolean getClickable() {
        return _clickable;
    }

    /**
     * Set visible property value. Visible affects whether the MapItem is draw and whether it is hit
     * tested.
     *
     * @param visible the visible value
     * @param ignoreConditions True to set the visibility condition to ignore if
     *                         it conflicts with the new visibility state
     */
    public void setVisible(boolean visible, boolean ignoreConditions) {
        if (visible != _visible) {
            _visible = visible;
            onVisibleChanged();
            // Parent group needs to be visible or clicks won't work
            if (visible && group != null && !group.getVisible())
                group.setVisibleIgnoreChildren(true);
        } else if (ignoreConditions && visible != getVisible()) {
            // Ignore visibility condition temporarily
            _visCond = VisibilityCondition.IGNORE;
            onVisibleChanged();
        }
    }

    public void setVisible(boolean visible) {
        setVisible(visible, true);
    }

    /**
     * Get the visible property value. Visible affects whether the MapItem is draw and whether it is
     * hit.
     *
     * @param ignoreConditions True to ignore the visibility condition state
     * @return true if visible
     */
    public boolean getVisible(boolean ignoreConditions) {
        return _visible && (ignoreConditions
                || _visCond != VisibilityCondition.INVISIBLE);
    }

    public boolean getVisible() {
        return getVisible(false);
    }

    /**
     * Set the ascending order of the MapItem. zOrder affects draw order and hit order.
     *
     * @param zOrder
     */
    public void setZOrder(final double zOrder) {
        //Log.d("MapItem", "attempting to set setting zorder on: " + getUID() + " " +
        //                   getMetaString("callsign", "[notset]") + " to: " + zOrder +
        //                   " from: " + _zOrder);
        if (_zOrder != zOrder) {
            _zOrder = zOrder;
            onZOrderChanged();
        }

    }

    /**
     * Get the item zOrder.
     *
     * @return
     */
    public double getZOrder() {
        return _zOrder;
    }

    public void setEditable(boolean editable) {
        this.setMetaBoolean("editable", editable);
    }

    final boolean DEFAULT_EDITABLE = false;

    public boolean getEditable() {
        return this.getMetaBoolean("editable", DEFAULT_EDITABLE);
    }

    public void setMovable(boolean movable) {
        this.setMetaBoolean("movable", movable);
    }

    final boolean DEFAULT_MOVABLE = false;

    public boolean getMovable() {
        return this.getMetaBoolean("movable", DEFAULT_MOVABLE);
    }

    /**
     * Test if a pixel is contained within the bounds of the MapItem on the orthographic projected
     * map.
     *
     * @param xpos pixel x location (increases left -> right)
     * @param ypos pixel y location (increases top -> bottom)
     * @param point the pixel location as projected onto the map surface (lat, lng of pixel
     *            location)
     * @param view the map view
     * @return true when a hit occurs
     */
    public boolean testOrthoHit(int xpos, int ypos, GeoPoint point,
            MapView view) {
        return false;
    }

    /**
     * Set whether this map item is touchable (used by testOrthoHit)
     * @param state True if touchable
     */
    public void setTouchable(boolean state) {
        setMetaBoolean("touchable", state);
    }

    public boolean isTouchable() {
        return getMetaBoolean("touchable", true);
    }

    /**
     * Get the hit/touch radius for this map item
     * @param view Map view
     * @return Hit radius in floating pixels
     */
    public float getHitRadius(MapView view) {
        return (float) (HIT_RATIO_DEFAULT * view.getDisplayDpi());
    }

    /**
     * Set a client-specific object used distinguish this MapItem from others. This is NOT an
     * observable property.
     *
     * @param tag client-specific unique tag
     */
    public void setTag(Object tag) {
        _tag = tag;
    }

    /**
     * Get the client-specific object used to distinguish this MapItem from others. This is NOT an
     * observable property
     *
     * @return client-specific unique tag
     */
    public Object getTag() {
        return _tag;
    }

    /**
     * Get the parent map group to which this item belongs to
     * Note: This method is intentionally non-synchronized due to noted
     * performance issues
     *
     * @return Map group or null if N/A
     */
    public MapGroup getGroup() {
        return group;
    }

    /**
     * Helper method for removing an item from its map group
     * In most circumstances, this will remove the item from the map
     *
     * @return True if group found, false if no group found
     */
    public boolean removeFromGroup() {
        final MapGroup group = getGroup();
        if (group != null) {
            group.removeItem(this);
            return true;
        }
        return false;
    }

    /**
     * Invokes when the clickable property changes
     */
    protected void onClickableChanged() {
        for (OnClickableChangedListener l : _clickableListeners) {
            l.onClickableChanged(this);
        }
    }

    /**
     * Invokes when the visible property changes
     */
    protected void onVisibleChanged() {
        for (OnVisibleChangedListener l : _visibleListeners) {
            l.onVisibleChanged(this);
        }
    }

    /**
     * Invokes when the type property changes
     */
    protected void onTypeChanged() {
        for (OnTypeChangedListener l : _typeListeners) {
            l.onTypeChanged(this);
        }
    }

    /**
     * Invokes when the zOrder property changes
     */
    protected void onZOrderChanged() {
        for (OnZOrderChangedListener l : _zOrderListeners) {
            l.onZOrderChanged(this);
        }
    }

    /**
     * Invoked when the height property changes
     */
    protected void onHeightChanged() {
        for (OnHeightChangedListener l : _onHeightChanged) {
            l.onHeightChanged(this);
        }
    }

    /**
     * Invokes when the metadata property changes
     */
    public void notifyMetadataChanged(final String field) {
        for (OnMetadataChangedListener l : _onMetadataChangedListeners) {
            l.onMetadataChanged(this, field);
        }
    }

    /**
     * Clean up all of the resources associated with this MapItem, for destruction.
     */
    public synchronized void dispose() {
        _visibleListeners.clear();
        _typeListeners.clear();
        _clickableListeners.clear();
        _zOrderListeners.clear();
        group = null;
    }

    public void onAdded(MapGroup parent) {
        this.group = parent;
        this.onGroupChanged(true, parent);
    }

    public void onRemoved(MapGroup parent) {
        final MapGroup old = this.group;
        this.group = null;
        this.onGroupChanged(false, old);
    }

    public void addOnGroupChangedListener(OnGroupChangedListener listener) {
        _onGroupListeners.add(listener);
    }

    public void removeOnGroupChangedListener(OnGroupChangedListener listener) {
        _onGroupListeners.remove(listener);
    }

    protected void onGroupChanged(boolean added, MapGroup mapGroup) {
        for (OnGroupChangedListener l : _onGroupListeners) {
            if (added)
                l.onItemAdded(this, mapGroup);
            else
                l.onItemRemoved(this, mapGroup);
        }
    }

    /**
     * Add a height changed property listener
     *
     * @param listener the listener
     */
    public void addOnHeightChangedListener(OnHeightChangedListener listener) {
        _onHeightChanged.add(listener);
    }

    /**
     * Remove a height changed property listener
     *
     * @param listener the listener
     */
    public void removeOnHeightChangedListener(
            OnHeightChangedListener listener) {
        _onHeightChanged.remove(listener);
    }

    public long getSerialId() {
        return _globalId;
    }

    /**
     * Dispatches a persist event for the MapItem. The refresh event will signal interested
     * components that the item should be persisted to its storage.
     * <P>
     * By default, the event will be dispatched with the extra, <code>"internal"</code>, set to
     * <code>true</code>. This may be overridden by setting the parameter in
     * <code>persistExtras</code> to an alternate value.
     *
     * @param dispatcher The event dispatcher
     * @param persistExtras An optional bundle of extras to be sent with the event; may be
     *            <code>null</code>
     * @param clazz the class that made this persist call
     * See {@link MapEvent#ITEM_PERSIST}
     */
    public void persist(MapEventDispatcher dispatcher, Bundle persistExtras,
            Class<?> clazz) {
        Bundle extras = new Bundle();
        extras.putBoolean("internal", true);
        //TODO for now this is used for logging, but could be in used in conjunction or to replace "from"
        extras.putString("fromClass", clazz.getName());
        if (persistExtras != null)
            extras.putAll(persistExtras);

        //update marker timestamp based on extra, or current time
        if (!extras.containsKey("lastUpdateTime")) {
            extras.putLong("lastUpdateTime",
                    new CoordinatedTime().getMilliseconds());
            //Log.d(TAG, getUID() + " persist Generating lastUpdateTime: " + extras.getLong("lastUpdateTime") + ", from: " + clazz.getName());
        }
        //        else{
        //            Log.d(TAG, getUID() + " persist Using lastUpdateTime: " + extras.getLong("lastUpdateTime") + ", from: " + clazz.getName());
        //        }
        setMetaLong("lastUpdateTime", extras.getLong("lastUpdateTime"));

        MapEvent.Builder b = new MapEvent.Builder(MapEvent.ITEM_PERSIST);
        dispatcher.dispatch(b.setItem(this)
                .setExtras(extras)
                .setFrom(clazz)
                .build());
    }

    /**
     * Dispatches a refresh event for the MapItem. The refresh event will signal interested
     * components that the metadata properties of the item have changed.
     *
     * @param dispatcher The event dispatcher
     * @param refreshExtras An optional bundle of extras to be sent with the event; may be
     *            <code>null</code>
     * See {@link MapEvent#ITEM_REFRESH}
     */
    public void refresh(MapEventDispatcher dispatcher, Bundle refreshExtras,
            Class<?> clazz) {
        Bundle extras = new Bundle();
        //TODO for now this is used for logging, but could be in used in conjunction or to replace "from"
        extras.putString("fromClass", clazz.getName());
        if (refreshExtras != null)
            extras.putAll(refreshExtras);

        //update marker timestamp based on extra, or current time
        if (!extras.containsKey("lastUpdateTime")) {
            extras.putLong("lastUpdateTime",
                    new CoordinatedTime().getMilliseconds());
            //Log.d(TAG, getUID() + " refresh Generating lastUpdateTime: " + extras.getLong("lastUpdateTime") + ", from: " + clazz.getName());
        }
        //         else{
        //             Log.d(TAG, getUID() + " refresh Using lastUpdateTime: " + extras.getLong("lastUpdateTime") + ", from: " + clazz.getName());
        //         }
        setMetaLong("lastUpdateTime", extras.getLong("lastUpdateTime"));

        MapEvent.Builder b = new MapEvent.Builder(MapEvent.ITEM_REFRESH);
        dispatcher.dispatch(b.setItem(this)
                .setExtras(extras)
                .setFrom(clazz)
                .build());
    }

    /**************************************************************************/

    public static long createSerialId() {
        return serialIdGenerator.incrementAndGet();
    }

    public static String getUniqueMapItemName(MapItem mapItem) {
        String ret = "";
        MapGroup group = mapItem.getGroup();
        if (group != null) {
            ret += group.getFriendlyName() + ".";
        }
        ret += ATAKUtilities.getDisplayName(mapItem);
        return ret;
    }

    public static double computeDistance(MapItem item, GeoPoint point) {
        if (item instanceof PointMapItem)
            return DistanceCalculations.calculateRange(
                    ((PointMapItem) item).getPoint(), point);
        else if (item instanceof Shape)
            return DistanceCalculations.calculateRange(
                    ((Shape) item).getCenter().get(), point);
        else if (item instanceof AnchoredMapItem)
            return DistanceCalculations.calculateRange(((AnchoredMapItem) item)
                    .getAnchorItem()
                    .getPoint(), point);
        else
            return Double.NaN;
    }

    @Override
    public void copyMetaData(final java.util.Map<String, Object> bundle) {
        // cannot replace the uid //
        bundle.remove("uid");

        super.copyMetaData(bundle);

        setType(super.getMetaString("type", EMPTY_TYPE));
    }

    /**
     * Get the metadata key for where remarks are stored
     * 99.9% of the time this is just "remarks". But unfortunately some items,
     * such as CASEVAC, use a different key.
     * @return Remarks metadata key
     */
    protected String getRemarksKey() {
        return "remarks";
    }

    /**
     * Set map item remarks (may contain hashtags)
     * @param remarks Remarks string
     */
    public void setRemarks(String remarks) {
        super.setMetaString(getRemarksKey(), remarks);

        List<String> newTags = HashtagUtils.extractTags(remarks);

        // Associate this map item with the manager
        HashtagManager.getInstance().updateContent(this, newTags);

        // Update internal hashtags
        _hashtags.clear();
        _hashtags.addAll(newTags);
    }

    public String getRemarks() {
        return getMetaString(getRemarksKey(), "");
    }

    // HashtagContent overrides //

    @Override
    public void setHashtags(Collection<String> tags) {

        // Remove old tags
        String remarks = getMetaString(getRemarksKey(), "").trim();
        for (String tag : _hashtags) {
            if (!tags.contains(tag))
                remarks = remarks.replace(tag + " ", "").replace(tag, "");
        }

        // Add new tags to remarks
        StringBuilder sb = new StringBuilder(remarks.trim());
        for (String tag : tags) {
            if (!_hashtags.contains(tag)) {
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(tag);
            }
        }

        setRemarks(sb.toString());

        MapView mv = MapView.getMapView();
        if (mv != null)
            persist(mv.getMapEventDispatcher(), null, getClass());
    }

    @Override
    public HashtagSet getHashtags() {
        return _hashtags.clone();
    }

    @Override
    public String getURI() {
        return URIHelper.getURI(this);
    }

    @Override
    public Drawable getIconDrawable() {
        Bitmap icon = ATAKUtilities.getIconBitmap(this);
        return icon != null ? new BitmapDrawable(icon) : null;
    }

    @Override
    public int getIconColor() {
        try {
            return (getMetaInteger("color", Color.WHITE) & 0xFFFFFF)
                    + 0xFF000000;
        } catch (Exception ignored) {
            return Color.WHITE;
        }
    }

    /**
     * Set the height of this item
     *
     * @param height the height in meters or Double.NaN if unknown.
     */
    public void setHeight(final double height) {
        if (Double.compare(getHeight(), height) != 0) {
            super.setMetaDouble("height", height);
            onHeightChanged();
        }
    }

    /**
     * Get the height property value or Double.NaN is to known.
     *
     * @return the height in meters.
     */
    public double getHeight() {
        return getMetaDouble("height", Double.NaN);
    }

    @Override
    public void setMetaDouble(String key, double value) {
        if (key.equals("height"))
            setHeight(value);
        else
            super.setMetaDouble(key, value);
    }

    @Override
    public void onVisibilityConditions(List<VisibilityCondition> conditions) {
        // Check visibility conditions that apply to this map item
        int newVis = VisibilityUtil.checkConditions(this, conditions);
        if (_visCond != newVis) {
            _visCond = newVis;
            onVisibleChanged();
        }
    }
}
