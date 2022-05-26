
package com.atakmap.android.maps;

import android.util.Pair;

import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.HashtagSearch;
import com.atakmap.android.hashtags.util.HashtagUtils;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.atakmap.map.hittest.HitTestControl;
import com.atakmap.util.WildCard;

/**
 * Collection of MapItems and child MapGroups. MapGroup's also contain dynamic properties which are
 * observable. These properties are used to define component or user specific details about the
 * MapItems in the group and child groups. A child group with the same dynamic property as a parent
 * group in a sense overrides it's parents dynamic property when considering it own MapItems. Some
 * built in core-components use pre-defined dynamic properties (such as the GLMapComponent for
 * coloring and styling).
 * 
 * 
 */
public abstract class MapGroup extends FilterMetaDataHolder implements
        DeepMapItemQuery, HashtagSearch {

    private final ConcurrentLinkedQueue<OnItemListChangedListener> _onItemListChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnVisibleChangedListener> _onVisibleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnFriendlyNameChangedListener> _onFriendlyNameChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnGroupListChangedListener> _onGroupListChanged = new ConcurrentLinkedQueue<>();
    private final long _runtimeId;
    private MapGroup _parent;
    private double _defaultZOrder = Double.NaN;

    /**
     * Create an empty MapGroup with a new serial ID.
     */
    public MapGroup() {
        this(new DefaultMetaDataHolder());
    }

    public MapGroup(MetaDataHolder metadata) {
        this(createMapGroupSerialId(), metadata);
    }

    protected MapGroup(long serialId) {
        this(serialId, new DefaultMetaDataHolder());
    }

    protected MapGroup(long serialId, MetaDataHolder metadata) {
        super(metadata);

        _runtimeId = serialId;
    }

    public final long getSerialId() {
        return _runtimeId;
    }

    public abstract void clearItems();

    public abstract void clearGroups();

    public abstract void setVisible(boolean visible);

    /**
     * Set group visibility without modifying the visibility of
     * children items
     */
    public void setVisibleIgnoreChildren(boolean visible) {
        setVisible(visible);
    }

    public abstract boolean getVisible();

    /**
     * Set the default z-order for any items added to this group
     *
     * @param zOrder Z-order or NaN to ignore
     */
    public void setDefaultZOrder(double zOrder) {
        _defaultZOrder = zOrder;
    }

    /**
     * Add a groupList listener
     * 
     * @param listener the listener
     */
    public final void addOnGroupListChangedListener(
            OnGroupListChangedListener listener) {
        _onGroupListChanged.add(listener);
    }

    /**
     * Remove a groupList listener
     * 
     * @param listener the listener
     */
    public final void removeOnGroupListChangedListener(
            OnGroupListChangedListener listener) {
        _onGroupListChanged.remove(listener);
    }

    public interface OnGroupListChangedListener {
        void onGroupAdded(MapGroup group, MapGroup parent);

        void onGroupRemoved(MapGroup group, MapGroup parent);
    }

    /**
     * Get the number of child MapGroups
     * 
     * @return the number of child groups
     */
    public abstract int getGroupCount();

    /**
     * Remove a MapGroup if it belongs to this one. If the group has its <code>permaGroup</code>
     * metadata set to <code>true</code>, it will remain a child BUT all of its child groups and
     * items will be removed.
     * 
     * @param group the MapGroup to remove
     * @return true if group belonged and was removed
     */
    public final boolean removeGroup(MapGroup group) {
        return this.removeGroupImpl(group, false);
    }

    /**
     * Implements group removal.
     * 
     * @param group the MapGroup to remove
     * @param referenceOnly If <code>true</code> only references to the group should be removed.
     *            This will be the common case when the remove is effected by adding the child to
     *            another group. If <code>false</code> it is assumed that the group is being
     *            disposed of.
     * @return true if group belonged and was removed
     */
    protected abstract boolean removeGroupImpl(MapGroup group,
            boolean referenceOnly);

    /**
     * Add a MapGroup
     * 
     * @param groupIface the MapGroup to add
     */
    public abstract MapGroup addGroup(MapGroup groupIface);

    /**
     * Create and add a MapGroup
     * 
     * @return the freshly created and added MapGroup
     */
    public abstract MapGroup addGroup();

    /**
     * Create and add a MapGroup with a friendly display name
     * 
     * @param friendlyName the friendly display name
     * @return the freshly created and added MapGroup
     */
    public abstract MapGroup addGroup(String friendlyName);

    /**
     * Add a itemList listener
     * 
     * @param listener the listener
     */
    public final void addOnItemListChangedListener(
            OnItemListChangedListener listener) {
        _onItemListChanged.add(listener);
    }

    /**
     * Add a itemList listener
     * 
     * @param listener the listener
     */
    public final void removeOnItemListChangedListener(
            OnItemListChangedListener listener) {
        _onItemListChanged.remove(listener);
    }

    public interface OnItemListChangedListener {
        void onItemAdded(MapItem item, MapGroup group);

        void onItemRemoved(MapItem item, MapGroup group);
    }

    /**
     * Get the number of MapItems in the group
     * 
     * @return the number of map items without counting any sub groups.
     */
    public abstract int getItemCount();

    /**
     * Get the parent MapGroup
     * 
     * @return null if is a root group or belongs to no group
     */
    public MapGroup getParentGroup() {
        return _parent;
    }

    /**
     * Add an item to the group.   If the item exists in another group, remove it first.
     * 
     * @param item the item to add
     */
    public void addItem(MapItem item) {
        if (item == null || item.getGroup() == this)
            return;
        MapGroup root = this;
        while (root.getParentGroup() != null)
            root = root.getParentGroup();

        try {
            final MapGroup group = item.getGroup();
            if (group != null) {
                item.setMetaBoolean("__groupTransfer", true);
                group.removeItem(item);
            }

            item.onAdded(this);
            this.addItemImpl(item);
            this.dispatchItemAdded(item);
        } finally {
            item.removeMetaData("__groupTransfer");
        }
        if (!Double.isNaN(_defaultZOrder))
            item.setZOrder(_defaultZOrder);
    }

    /**
     * Create a marker and specifically add it to the this group.
     */
    public Marker createMarker(final GeoPoint point, String uid) {
        final Marker marker = new Marker(point, uid);
        addItem(marker);
        return marker;
    }

    /**
     * Performs the actual addition of the item. The {@link #dispatchItemAdded(MapItem)} method will
     * be invoked immediately after this method returns.
     * 
     * @param item The item to be added
     */
    protected abstract void addItemImpl(MapItem item);

    public MapItem findItem(String key, String value) {
        return this.findItem(Collections.singletonMap(key, value));
    }

    public abstract MapItem findItem(Map<String, String> metadata);

    public List<MapItem> findItems(String key, String value) {
        return this.findItems(Collections.singletonMap(key, value));
    }

    public abstract List<MapItem> findItems(Map<String, String> metadata);

    /**
     * Stock implementation of searching for a UID.
     */
    public MapItem deepFindUID(final String uid) {
        return deepFindItem(Collections.singletonMap("uid", uid));
    }

    /**
     * Deep traverse the map group to find an item that matches search constraints.
     * @param key the key to seach on
     * @param value the value for the key
     * @return the first map item that contains the key specified with the matching value.
     */
    public MapItem deepFindItem(final String key, final String value) {
        if (key.equals("uid"))
            return deepFindUID(value);

        return deepFindItem(Collections.singletonMap(key, value));
    }

    /**
     * Deep traverse the map group to find an item that matches search constraints.
     * @param metadata the map of keys and values that need to be matched when searching for an item.
     * @return the first map item that contains the all of the keys and values matched. 
     */
    @Override
    public abstract MapItem deepFindItem(Map<String, String> metadata);

    /**
     * Deep traverse the map group to find all items that matches search constraints.
     * @param key the key to seach on
     * @param value the value for the key
     * @return the list of  map items that contains the key specified with the matching value.
     */
    public List<MapItem> deepFindItems(final String key, final String value) {
        return deepFindItems(Collections.singletonMap(key, value));
    }

    /**
     * Find the closest item to the geosptial point or an empty map if nothing is found.
     * @param location the point to search with.
     */
    public MapItem findClosestItem(GeoPoint location) {
        return this.findClosestItem(location, 0.0d,
                Collections.<String, String> emptyMap());
    }

    public MapItem findClosestItem(GeoPoint location, String key,
            String value) {
        return this.findClosestItem(location,
                Collections.singletonMap(key, value));
    }

    public MapItem findClosestItem(GeoPoint location,
            Map<String, String> metadata) {
        return this.findClosestItem(location, 0.0d, metadata);
    }

    /**
     * Finds the closest MapItem to the specified point, that falls within the specified threshold.
     * 
     * @param location The location
     * @param threshold The search threshold, in meters. Only items that are within this distance
     *            from the point are considered. If no threshold is desired, the value
     *            <code>0.0d</code> should be specified.
     * @return
     */
    public MapItem findClosestItem(GeoPoint location, double threshold) {
        return this.findClosestItem(location, threshold,
                Collections.<String, String> emptyMap());
    }

    public MapItem findClosestItem(GeoPoint location, double threshold,
            String key, String value) {
        return this.findClosestItem(location, threshold,
                Collections.singletonMap(key, value));
    }

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
    public abstract MapItem findClosestItem(GeoPoint location,
            double threshold,
            Map<String, String> metadata);

    public MapItem deepFindClosestItem(GeoPoint location) {
        return this.deepFindClosestItem(location, 0.0d,
                Collections.<String, String> emptyMap());
    }

    public MapItem deepFindClosestItem(GeoPoint location, String key,
            String value) {
        return this.deepFindClosestItem(location, 0.0d,
                Collections.singletonMap(key, value));
    }

    public MapItem deepFindClosestItem(GeoPoint location,
            Map<String, String> metadata) {
        return this.deepFindClosestItem(location, 0.0d, metadata);
    }

    public MapItem deepFindClosestItem(GeoPoint location, double threshold) {
        return this.deepFindClosestItem(location, threshold,
                Collections.<String, String> emptyMap());
    }

    public MapItem deepFindClosestItem(GeoPoint location, double threshold,
            String key, String value) {
        return this.deepFindClosestItem(location, threshold,
                Collections.singletonMap(key, value));
    }

    @Override
    public MapItem deepFindClosestItem(GeoPoint location, double threshold,
            Map<String, String> metadata) {
        MapItem candidate = this.findClosestItem(location, threshold, metadata);
        double candidateDistance = Double.POSITIVE_INFINITY;
        if (candidate != null)
            candidateDistance = MapItem.computeDistance(candidate, location);

        Collection<MapGroup> children = this.getChildGroups();

        MapItem result;
        double resultDistance;
        for (MapGroup child : children) {
            result = child.deepFindClosestItem(location, threshold, metadata);
            if (result != null
                    && (candidate == null || Double.isNaN(candidateDistance))) {
                candidate = result;
                candidateDistance = MapItem
                        .computeDistance(candidate, location);
            } else if (result != null && candidate != null) {
                resultDistance = MapItem.computeDistance(result, location);
                if (!Double.isNaN(resultDistance)
                        && resultDistance < candidateDistance) {
                    candidate = result;
                    candidateDistance = resultDistance;
                }
            }
        }

        return candidate;
    }

    /**
     * Finds all MapItems within the radius around the specified point.
     * 
     * @param location The location
     * @param radius The search threshold, in meters. 
     * @return the collection of map items that fall within the radius of the specified location.
     */
    public Collection<MapItem> findItems(GeoPoint location, double radius) {
        return this.findItems(location, radius,
                Collections.<String, String> emptyMap());
    }

    public Collection<MapItem> findItems(GeoPoint location, double radius,
            String key, String value) {
        return this.findItems(location, radius,
                Collections.singletonMap(key, value));
    }

    public abstract Collection<MapItem> findItems(GeoPoint location,
            double radius,
            Map<String, String> metadata);

    public Collection<MapItem> deepFindItems(GeoPoint location, double radius) {
        return this.deepFindItems(location, radius,
                Collections.<String, String> emptyMap());
    }

    public Collection<MapItem> deepFindItems(GeoPoint location, double radius,
            String key,
            String value) {
        return this.deepFindItems(location, radius,
                Collections.singletonMap(key, value));
    }

    @Override
    public Collection<MapItem> deepFindItems(GeoPoint location, double radius,
            Map<String, String> metadata) {
        LinkedList<MapItem> retval = new LinkedList<>(
                this.findItems(location, radius, metadata));

        Collection<MapGroup> children = this.getChildGroups();
        for (MapGroup child : children)
            retval.addAll(child.deepFindItems(location, radius, metadata));

        return retval;
    }

    /**
     * @deprecated This is now handled by the {@link RootMapGroup} via
     * {@link HitTestControl}. Implementation will be kept intact until
     * deprecation removal.
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    protected MapItem hitTest(final int xpos, final int ypos,
            final GeoPoint point, final MapView view) {

        SortedSet<MapItem> hits = hitTestItems(xpos, ypos, point, view);
        if (hits == null)
            return null;
        else
            return hits.first();
    }

    /**
     * @deprecated This is now handled by the {@link RootMapGroup} via
     * {@link HitTestControl}. Implementation will be kept intact until
     * deprecation removal.
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    protected SortedSet<MapItem> hitTestItems(final int xpos, final int ypos,
            final GeoPoint point, final MapView view) {

        if (!this.getVisible())
            return null;
        Collection<MapItem> items = this.getItems();

        SortedSet<MapItem> hits = new TreeSet<>(
                MapItem.ZORDER_HITTEST_COMPARATOR);
        for (MapItem item : items)
            if (item.getVisible() && item.getClickable()
                    && item.testOrthoHit(xpos, ypos, point, view))
                hits.add(item);

        if (hits.isEmpty()) {
            return null;
        } else {
            return hits;
        }

    }

    @Override
    public MapItem deepHitTest(int xpos, int ypos, GeoPoint point,
            MapView view) {
        MapItem candidate = this.hitTest(xpos, ypos, point, view);

        Collection<MapGroup> children = this.getChildGroups();

        MapItem result;
        for (MapGroup child : children) {
            result = child.deepHitTest(xpos, ypos, point, view);
            if (result == null)
                continue;
            if ((candidate == null)
                    || (result.getZOrder() < candidate.getZOrder()))
                candidate = result;
        }

        return candidate;
    }

    // RC/AML/2015-01-20: Search map groups and return sorted list of tracks that are hit.
    @Override
    public SortedSet<MapItem> deepHitTestItems(int xpos, int ypos,
            GeoPoint point, MapView view) {
        SortedSet<MapItem> candidate = this.hitTestItems(xpos, ypos, point,
                view);

        Collection<MapGroup> children = this.getChildGroups();

        SortedSet<MapItem> result;
        for (MapGroup child : children) {
            result = child.deepHitTestItems(xpos, ypos, point, view);
            if (result == null)
                continue;

            if (candidate == null)
                candidate = result;
            else
                candidate.addAll(result);
        }

        return candidate;
    }

    /**
     * Finds and returns THE FIRST child map group of this map group with a friendly name that
     * matches 'friendlyname'. NOTE: MapGroup names are not guaranteed to be unique. Make sure you
     * know where you're searching before you grab a map group that may not be the one you actually
     * want.
     * 
     * @param friendlyname the friendly name of the group to find
     * @return the FIRST map group encountered with a matching friendly name or null if it was not
     *         found
     */
    public MapGroup findMapGroup(String friendlyname) {
        Collection<MapGroup> children = this.getChildGroups();
        for (MapGroup child : children) {
            if (Objects.equals(child.getFriendlyName(), friendlyname))
                return child;
        }
        return null;
    }

    /**
     * Finds and returns THE FIRST child map group of this map group with a friendly name that
     * matches 'friendlyname' in a breadth-first-search process. This means it will search all of
     * its children before going back and checking its children's groups. NOTE: MapGroup names are
     * not guaranteed to be unique. Make sure you know where you're searching before you grab a map
     * group that may not be the one you actually want.
     * 
     * @param friendlyname the friendly name of the group to find
     * @return the FIRST map group encountered with a matching friendly name or null if it was not
     *         found
     */
    public MapGroup deepFindMapGroup(String friendlyname) {
        MapGroup retval = this.findMapGroup(friendlyname);
        if (retval != null)
            return retval;
        Collection<MapGroup> children = this.getChildGroups();
        for (MapGroup child : children) {
            retval = child.deepFindMapGroup(friendlyname);
            if (retval != null)
                return retval;
        }
        return null;
    }

    /**
     * Executes the callback on each of this group's child items until a value of <code>true</code>
     * is returned by the callback.
     * 
     * @param cb
     * @return
     */
    public abstract boolean forEachItem(final MapItemsCallback cb);

    public abstract boolean deepForEachItem(final MapItemsCallback cb);

    /**
     * Get the friendly name of the MapGroup
     * 
     * @return
     */
    public abstract String getFriendlyName();

    public abstract void setFriendlyName(String friendlyName);

    /**
     * Invoked when a MapItem is added
     * 
     * @param itemIface the MapItem that was added
     */
    protected final void dispatchItemAdded(MapItem itemIface) {
        for (OnItemListChangedListener l : _onItemListChanged) {
            l.onItemAdded(itemIface, this);
        }
    }

    /**
     * Invoked when a MapItem is removed
     * 
     * @param itemIface the MapItem that was removed
     */
    protected void dispatchItemRemoved(MapItem itemIface) {
        for (OnItemListChangedListener l : _onItemListChanged) {
            l.onItemRemoved(itemIface, this);
        }
    }

    /**
     * Invoked when a MapGroup is added
     * 
     * @param group the MapGroup that was added
     */
    protected final void dispatchGroupAdded(MapGroup group) {
        for (OnGroupListChangedListener l : _onGroupListChanged) {
            l.onGroupAdded(group, this);
        }
    }

    /**
     * Invoked when a MapGroup is removed
     * 
     * @param group the MapGroup that was removed
     */
    protected final void dispatchGroupRemoved(MapGroup group) {
        for (OnGroupListChangedListener l : _onGroupListChanged) {
            l.onGroupRemoved(group, this);
        }
    }

    protected final void dispatchVisibleChanged() {
        for (OnVisibleChangedListener l : _onVisibleChanged) {
            l.onGroupVisibleChanged(this);
        }
    }

    protected final void dispatchFriendlyNameChanged() {
        for (OnFriendlyNameChangedListener l : _onFriendlyNameChanged) {
            l.onFriendlyNameChanged(this);
        }
    }

    public void addOnVisibleChangedListener(OnVisibleChangedListener l) {
        _onVisibleChanged.add(l);
    }

    public final void removeOnVisibleChangedListener(
            OnVisibleChangedListener l) {
        _onVisibleChanged.remove(l);
    }

    public interface OnVisibleChangedListener {
        void onGroupVisibleChanged(MapGroup group);
    }

    public final void addOnFriendlyNameChangedListener(
            OnFriendlyNameChangedListener l) {
        _onFriendlyNameChanged.add(l);
    }

    public final void removeOnFriendlyNameChangedListener(
            OnFriendlyNameChangedListener l) {
        _onFriendlyNameChanged.remove(l);
    }

    public interface OnFriendlyNameChangedListener {
        void onFriendlyNameChanged(MapGroup group);
    }

    protected final void onAddedNoSync(MapGroup newParent) {
        _parent = newParent;
    }

    protected final void onRemovedNoSync() {
        _parent = null;
    }

    protected final boolean hasItemListChangedListeners() {
        return !_onItemListChanged.isEmpty();
    }

    /**
     * Removes the given MapItem from this map group, if it is a member.
     *
     * If it was not a member, returns normally, without throwing an exception.
     */
    public final void removeItem(final MapItem item) {
        if (item == null)
            return;
        if (this.removeItemImpl(item)) {
            item.onRemoved(this);
            this.dispatchItemRemoved(item);
        }
    }

    /**
     * Implementation for removing an item from a map group.   This will
     * return true if the item was in the map group and removed, otherwise it will
     * return false if the item was not in the map group.
     */
    protected abstract boolean removeItemImpl(final MapItem item);

    public abstract Collection<MapGroup> getChildGroups();

    public abstract Collection<MapItem> getItems();

    public abstract MapGroup getChildGroupById(long id);

    public abstract MapItem getItemById(long id);

    public boolean containsItem(MapItem item) {
        return (item != null) && (item == this.getItemById(item.getSerialId()));
    }

    /**
     * Get a flat list of all map items in the group hierarchy
     * @return List of map items
     */
    public Collection<MapItem> getItemsRecursive() {
        List<MapItem> items = new ArrayList<>(getItems());
        Collection<MapGroup> childGroups = getChildGroups();
        for (MapGroup group : childGroups)
            items.addAll(group.getItemsRecursive());
        return items;
    }

    @Override
    public Collection<HashtagContent> search(Collection<String> tags) {
        List<HashtagContent> ret = new ArrayList<>();

        // Search child groups
        for (MapGroup group : getChildGroups())
            ret.addAll(HashtagUtils.search(group, tags));

        // Search child items
        for (MapItem item : getItems()) {
            if (HashtagUtils.search(item, tags))
                ret.add(item);
        }

        return ret;
    }

    /**************************************************************************/

    private final static AtomicLong serialIdGenerator = new AtomicLong(0L);

    public static MapItem deepFindItemWithMetaString(MapGroup group,
            String key, String value) {
        return deepFindItemWithMetaString(group,
                Collections.singletonMap(key, value));
    }

    public static String getPath(MapGroup group) {
        StringBuilder retval = new StringBuilder();
        if (group.getParentGroup() != null)
            retval.append(getPath(group.getParentGroup()));
        retval.append("/").append(group.getFriendlyName());
        return retval.toString();
    }

    public static MapItem deepFindItemWithMetaString(MapGroup group,
            Map<String, String> metadata) {
        MapItem item = group.findItem(metadata);
        if (item == null) {
            for (MapGroup g : group.getChildGroups()) {
                item = g.deepFindItem(metadata);
                if (item != null) {
                    break;
                }
            }
        }
        return item;
    }

    public static MapItem findItemWithMetaString(MapGroup group, String key,
            String value) {
        return findItemWithMetaString(group,
                Collections.singletonMap(key, value));
    }

    /**
     * Finds the first child item that match the specified metadata filter.
     * Metadata matching is case-insensitive; the asterisk ('*') character may
     * be used as the wildcard.
     *  
     * @param group
     * @param metadata
     * @return
     */
    public static MapItem findItemWithMetaString(MapGroup group,
            Map<String, String> metadata) {
        Pair<Map<String, String>, Map<String, String>> literalAndRegex = getRegex(
                metadata);
        return findItemWithMetaStringImpl(group, literalAndRegex.first,
                literalAndRegex.second);
    }

    /**
     * Helper method to split literal and regex expressions
     * @param metadata
     * @return
     */
    private static Pair<Map<String, String>, Map<String, String>> getRegex(
            Map<String, String> metadata) {
        Map<String, String> literal = new HashMap<>();
        Map<String, String> regex = new HashMap<>();
        String k;
        String v;
        for (Map.Entry<String, String> meta : metadata.entrySet()) {
            k = meta.getKey();
            v = meta.getValue();
            if (k == null || v == null)
                continue;
            if (v.indexOf('*') >= 0)
                regex.put(k, WildCard.wildcardAsRegex(v, '*'));
            else
                literal.put(k, v);
        }

        return new Pair<>(literal,
                regex);
    }

    /**
     * Finds the first matching child item
     *
     * @param group
     * @param literal
     * @param regex
     * @return
     */
    public static MapItem findItemWithMetaStringImpl(MapGroup group,
            Map<String, String> literal, Map<String, String> regex) {
        if (group != null) {
            for (MapItem item : group.getItems()) {
                if (matchItemWithMetaString(item, literal, regex))
                    return item;
            }
        }
        return null;
    }

    /**
     * Check if the specified item matches the key/value pair
     *
     * @param item
     * @param key
     * @param value
     * @return
     */
    public static boolean matchItemWithMetaString(final MapItem item,
            final String key, final String value) {
        return matchItemWithMetaString(item,
                Collections.singletonMap(key, value));
    }

    /**
     * Check if the specified item matches the metadata filter
     *
     * @param item
     * @param metadata
     * @return
     */
    public static boolean matchItemWithMetaString(MapItem item,
            Map<String, String> metadata) {
        Pair<Map<String, String>, Map<String, String>> literalAndRegex = getRegex(
                metadata);
        return matchItemWithMetaString(item, literalAndRegex.first,
                literalAndRegex.second);
    }

    /**
     * Check if the map item matches the literal & regex expressions
     *
     * @param item
     * @param literal
     * @param regex
     * @return
     */
    public static boolean matchItemWithMetaString(final MapItem item,
            final Map<String, String> literal,
            final Map<String, String> regex) {
        boolean match = (literal.size() > 0 || regex.size() > 0);
        for (Map.Entry<String, String> entry : literal.entrySet()) {
            String v = item.getMetaString(entry.getKey(), null);
            match &= (v != null &&
                    (v.equals(entry.getValue()) || (v.equalsIgnoreCase(entry
                            .getValue()))));
        }
        for (Map.Entry<String, String> entry : regex.entrySet()) {
            String v = item.getMetaString(entry.getKey(), null);
            match &= (v != null && v.toLowerCase(LocaleUtil.getCurrent())
                    .matches(
                            entry.getValue()));
        }

        return match;
    }

    /**
     * Finds all descendant items that match the specified metadata filter.
     * Metadata matching is case-insensitive; the asterisk ('*') character may
     * be used as the wildcard.
     *  
     * @param outList
     * @param group
     * @param key
     * @parem value
     * @return
     * 
     * @see #findItemsWithMetaString(List, MapGroup, Map)
     */
    public static int deepFindItemsWithMetaString(List<MapItem> outList,
            MapGroup group,
            String key, String value) {
        return deepFindItemsWithMetaString(outList, group,
                Collections.singletonMap(key, value));
    }

    /**
     * Finds all descendant items that match the specified metadata filter.
     * Metadata matching is case-insensitive; the asterisk ('*') character may
     * be used as the wildcard.
     *  
     * @param outList
     * @param group
     * @param metadata
     * @return
     * 
     * @see #findItemsWithMetaString(List, MapGroup, Map)
     */
    public static int deepFindItemsWithMetaString(List<MapItem> outList,
            MapGroup group,
            Map<String, String> metadata) {

        Pair<Map<String, String>, Map<String, String>> literalAndRegex = getRegex(
                metadata);

        return deepFindItemsWithMetaStringImpl(outList, group,
                literalAndRegex.first, literalAndRegex.second);
    }

    private static int deepFindItemsWithMetaStringImpl(List<MapItem> outList,
            MapGroup group,
            Map<String, String> literal, Map<String, String> regex) {
        int count = findItemsWithMetaStringImpl(outList, group, literal, regex);
        for (MapGroup g : group.getChildGroups()) {
            count += deepFindItemsWithMetaStringImpl(outList, g, literal,
                    regex);
        }
        return count;
    }

    /**
     * Finds all child items that match the specified metadata filter. Metadata
     * matching is case-insensitive; the asterisk ('*') character may be used
     * as the wildcard.
     *  
     * @param outList
     * @param group
     * @param metadata
     * @return
     */
    public static int findItemsWithMetaString(List<MapItem> outList,
            MapGroup group,
            Map<String, String> metadata) {

        Pair<Map<String, String>, Map<String, String>> literalAndRegex = getRegex(
                metadata);

        return findItemsWithMetaStringImpl(outList, group,
                literalAndRegex.first, literalAndRegex.second);
    }

    private static int findItemsWithMetaStringImpl(List<MapItem> outList,
            MapGroup group,
            Map<String, String> literal, Map<String, String> regex) {
        int count = 0;
        for (MapItem item : group.getItems()) {
            if (matchItemWithMetaString(item, literal, regex)) {
                outList.add(item);
                count++;
            }
        }
        return count;
    }

    public static MapItem deepFindItemWithSerialId(MapGroup group,
            long serialId) {
        for (MapItem item : group.getItems())
            if (item.getSerialId() == serialId)
                return item;
        MapItem item;
        for (MapGroup childGroup : group.getChildGroups()) {
            item = deepFindItemWithSerialId(childGroup, serialId);
            if (item != null)
                return item;
        }
        return null;
    }

    public static MapGroup deepFindGroupByNameDepthFirst(MapGroup group,
            String friendlyname) {
        MapGroup result;
        for (MapGroup mg : group.getChildGroups()) {
            if (Objects.equals(mg.getFriendlyName(), friendlyname))
                return mg;
            result = deepFindGroupByNameDepthFirst(mg, friendlyname);
            if (result != null)
                return result;
        }
        return null;
    }

    public static MapGroup deepFindGroupByNameBreadthFirst(MapGroup group,
            String friendlyname) {
        for (MapGroup mg : group.getChildGroups())
            if (Objects.equals(mg.getFriendlyName(), friendlyname))
                return mg;

        MapGroup result;
        for (MapGroup mg : group.getChildGroups()) {
            result = deepFindGroupByNameBreadthFirst(mg, friendlyname);
            if (result != null)
                return result;
        }

        return null;
    }

    public static MapGroup deepFindGroupBySerialIdDepthFirst(MapGroup group,
            long serialId) {
        MapGroup result;
        for (MapGroup mg : group.getChildGroups()) {
            if (mg.getSerialId() == serialId)
                return mg;
            result = deepFindGroupBySerialIdDepthFirst(mg, serialId);
            if (result != null)
                return result;
        }
        return null;
    }

    public static MapGroup deepFindGroupBySerialIdBreadthFirst(MapGroup group,
            long serialId) {
        for (MapGroup mg : group.getChildGroups())
            if (mg.getSerialId() == serialId)
                return mg;
        MapGroup result = null;
        for (MapGroup mg : group.getChildGroups())
            result = deepFindGroupBySerialIdBreadthFirst(mg, serialId);
        if (result != null)
            return result;
        return null;
    }

    public interface MapItemsCallback {
        /**
         * @param item the map item triggered
         * @return <code>true</code> if the item matched, <code>false</code> otherwise.
         */
        boolean onItemFunction(MapItem item);

        final class And implements MapItemsCallback {
            private final MapItemsCallback func1;
            private final MapItemsCallback func2;

            public And(MapItemsCallback func1, MapItemsCallback func2) {
                this.func1 = func1;
                this.func2 = func2;
            }

            @Override
            public boolean onItemFunction(MapItem item) {
                return this.func1.onItemFunction(item)
                        && this.func2.onItemFunction(item);
            }
        }

        final class Or implements MapItemsCallback {
            private final MapItemsCallback func1;
            private final MapItemsCallback func2;

            public Or(MapItemsCallback func1, MapItemsCallback func2) {
                this.func1 = func1;
                this.func2 = func2;
            }

            @Override
            public boolean onItemFunction(MapItem item) {
                return this.func1.onItemFunction(item)
                        || this.func2.onItemFunction(item);
            }
        }

        final class Not implements MapItemsCallback {
            private final MapItemsCallback func;

            public Not(MapItemsCallback func) {
                this.func = func;
            }

            @Override
            public boolean onItemFunction(MapItem item) {
                return !this.func.onItemFunction(item);
            }
        }
    }

    public static abstract class OnItemCallback<T extends MapItem> implements
            MapItemsCallback {
        protected final Class<T> targetType;

        public OnItemCallback(Class<T> targetType) {
            this.targetType = targetType;
        }

        @Override
        public final boolean onItemFunction(MapItem item) {
            if (this.targetType.isInstance(item)) {
                return this.onMapItem(targetType.cast(item));
            }
            return false;
        }

        protected abstract boolean onMapItem(T item);
    }

    public static boolean mapItems(MapGroup group, MapItemsCallback func) {
        boolean r = false;
        for (MapItem i : group.getItems()) {
            if (r = func.onItemFunction(i)) {
                break;
            }
        }
        return r;
    }

    public static boolean deepMapItems(MapGroup group, MapItemsCallback func) {
        boolean r = mapItems(group, func);
        if (!r) {
            for (MapGroup g : group.getChildGroups()) {
                if (r = deepMapItems(g, func)) {
                    break;
                }
            }
        }
        return r;
    }

    /**
     * Returns a serial ID that across an invocation of ATAK that is unique against all other IDs
     * returned by this method.
     * <P>
     * <B>ALL IMPLEMENTERS OF <code>MapGroup</code> SHOULD DERIVE THEIR SERIAL ID USING THIS
     * METHOD!</B>
     * 
     * @return
     */
    public static long createMapGroupSerialId() {
        return serialIdGenerator.incrementAndGet();
    }

    public static int mapItemsCount(MapGroup group, MapItemsCallback func) {
        int r = 0;
        for (MapItem i : group.getItems())
            if (func.onItemFunction(i))
                r++;
        return r;
    }

    public static int deepMapItemsCount(MapGroup group, MapItemsCallback func) {
        int r = mapItemsCount(group, func);
        for (MapGroup g : group.getChildGroups())
            r += deepMapItemsCount(g, func);
        return r;
    }

    public static int deepMapItemsCount(MapGroup group) {
        int r = group.getItemCount();
        for (MapGroup g : group.getChildGroups())
            r += deepMapItemsCount(g);
        return r;
    }

    public static MapGroup findMapGroup(MapGroup group, String friendlyName) {
        if (friendlyName == null)
            throw new NullPointerException();
        for (MapGroup g : group.getChildGroups())
            if (friendlyName.equals(g.getFriendlyName()))
                return g;
        return null;
    }

}
