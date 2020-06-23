
package com.atakmap.android.maps;

import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
public class DefaultMapGroup extends MapGroup {
    /**
     * Create an empty MapGroup
     */
    public DefaultMapGroup() {
        this(null);
    }

    /**
     * Create a MapGroup with a friendly display name
     * 
     * @param friendlyName the friendly display name
     */
    public DefaultMapGroup(String friendlyName) {
        super();
        _friendlyName = friendlyName;
    }

    public DefaultMapGroup(String friendlyName, String overlay,
            boolean permaGroup) {
        this(friendlyName);
        setMetaString("overlay", overlay);
        setMetaBoolean("permaGroup", permaGroup);
    }

    public DefaultMapGroup(String friendlyName, String overlay,
            boolean permaGroup, double zOrder) {
        this(friendlyName, overlay, permaGroup);
        setDefaultZOrder(zOrder);
    }

    @Override
    public void clearItems() {
        ArrayList<MapItem> items = new ArrayList<>(_items.values());
        final boolean hasItemListChangedListeners = this
                .hasItemListChangedListeners();
        _items.clear();
        for (MapItem item : items) {
            item.onRemoved(this);
            if (hasItemListChangedListeners)
                this.dispatchItemRemoved(item);
            item.dispose();
        }
    }

    @Override
    public void clearGroups() {
        Iterator<MapGroup> iter = _groups.values().iterator();
        MapGroup toRemove;
        while (iter.hasNext()) {
            toRemove = iter.next();
            toRemove.clearItems();
            toRemove.clearGroups();
            if (!toRemove.getMetaBoolean("permaGroup", false)) {
                iter.remove();
                toRemove.onRemovedNoSync();
                this.dispatchGroupRemoved(toRemove);
            }
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (_visible != visible) {
            setVisibleIgnoreChildren(visible);

            // Sync visibility with children items
            Collection<MapItem> items = this.getItems();
            for (MapItem item : items)
                item.setVisible(visible);
            Collection<MapGroup> children = this.getChildGroups();
            for (MapGroup child : children)
                child.setVisible(visible);
        }
    }

    @Override
    public void setVisibleIgnoreChildren(boolean visible) {
        if (_visible != visible) {
            _visible = visible;
            // Parent group needs to be visible or clicks won't work
            if (visible && getParentGroup() != null
                    && !getParentGroup().getVisible())
                getParentGroup().setVisibleIgnoreChildren(true);
            this.dispatchVisibleChanged();
        }
    }

    @Override
    public boolean getVisible() {
        return _visible;
    }

    /**
     * Get the number of child MapGroups
     * 
     * @return the number of items in the group
     */
    @Override
    public int getGroupCount() {
        return _groups.size();
    }

    /**
     * Remove a MapGroup if it belongs to this one ;
     * 
     * @param group the MapGroup to remove
     * @return true if group belonged and was removed
     */
    @Override
    protected boolean removeGroupImpl(MapGroup group, boolean referenceOnly) {
        if (_groups.containsKey(group.getSerialId())) {
            final boolean perma = group.getMetaBoolean("permaGroup", false);
            if (referenceOnly) {
                _groups.remove(group.getSerialId());
                group.onRemovedNoSync();
                this.dispatchGroupRemoved(group);
                return true;
            } else {
                group.clearGroups();
                group.clearItems();
                if (!perma) {
                    _groups.remove(group.getSerialId());
                    group.onRemovedNoSync();
                    this.dispatchGroupRemoved(group);
                }
                return !perma;
            }
        }
        return false;
    }

    /**
     * Add a MapGroup
     * 
     * @param group the MapGroup to add
     * @return
     */
    @Override
    public MapGroup addGroup(MapGroup group) {
        if (group.getParentGroup() != null
                && !group.getParentGroup().removeGroupImpl(group, true))
            return null;
        _groups.put(group.getSerialId(), group);
        if (!_visible && group.getVisible())
            setVisibleIgnoreChildren(true);
        group.onAddedNoSync(this);
        this.dispatchGroupAdded(group);
        return group;
    }

    /**
     * Create and add a MapGroup
     * 
     * @return the freshly created and added MapGroup
     */
    @Override
    public MapGroup addGroup() {
        MapGroup group = new DefaultMapGroup();
        addGroup(group);
        return group;
    }

    /**
     * Create and add a MapGroup with a friendly display name
     * 
     * @param friendlyName the friendly display name
     * @return the freshly created and added MapGroup
     */
    @Override
    public MapGroup addGroup(String friendlyName) {
        MapGroup group = new DefaultMapGroup(friendlyName);
        addGroup(group);
        return group;
    }

    /**
     * Get the number of MapItems in the group
     * 
     * @return
     */
    @Override
    public int getItemCount() {
        return _items.size();
    }

    /**
     * Add an item to the group
     * 
     * @param item the item to add
     */
    @Override
    protected void addItemImpl(MapItem item) {
        _items.put(item.getSerialId(), item);

        // Adding a new item to an invisible group
        // This will NOT turn on visibility for existing
        // items that are invisible
        if (!_visible && item.getVisible())
            setVisibleIgnoreChildren(true);
    }

    @Override
    public MapItem deepFindUID(final String uid) {
        Collection<MapItem> items = this.getItems();
        for (MapItem item : items) {
            if (item.getUID().equals(uid))
                return item;
        }
        Collection<MapGroup> children = this.getChildGroups();
        for (MapGroup child : children) {
            if (child instanceof DefaultMapGroup) {
                MapItem m = child.deepFindUID(uid);
                if (m != null)
                    return m;
            } else {
                MapItem m = child.deepFindItem("uid", uid);
                if (m != null)
                    return m;
            }

        }
        return null;
    }

    /**
     * @param metadata is the map looking for matches with a key and a value.
     * @return
     */
    @Override
    public MapItem deepFindItem(Map<String, String> metadata) {
        return MapGroup.deepFindItemWithMetaString(this, metadata);
    }

    @Override
    public List<MapItem> deepFindItems(final Map<String, String> metadata) {
        final List<MapItem> list = new ArrayList<>();
        MapGroup.deepFindItemsWithMetaString(list, this, metadata);
        return list;
    }

    /**
     * @param meta is the map looking for matches with a key and a value.
     * @return
     */
    @Override
    public MapItem findItem(Map<String, String> meta) {
        return MapGroup.findItemWithMetaString(this, meta);
    }

    /**
     * @param meta is the map looking for matches with a key and a value.
     * @return
     */
    @Override
    public List<MapItem> findItems(Map<String, String> meta) {
        final List<MapItem> list = new ArrayList<>();
        MapGroup.findItemsWithMetaString(list, this, meta);
        return list;
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
    @Override
    public MapGroup findMapGroup(final String friendlyname) {
        for (MapGroup mg : _groups.values()) {
            if (Objects.equals(mg.getFriendlyName(), friendlyname)) {
                return mg;
            }
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
    @Override
    public MapGroup deepFindMapGroup(String friendlyname) {
        return MapGroup.deepFindGroupByNameBreadthFirst(this, friendlyname);
    }

    /**
     * Get the friendly name of the MapGroup
     * 
     * @return
     */
    @Override
    public String getFriendlyName() {
        return _friendlyName;
    }

    @Override
    public void setFriendlyName(String friendlyName) {
        if (!friendlyName.equals(_friendlyName)) {
            _friendlyName = friendlyName;
            this.dispatchFriendlyNameChanged();
        }
    }

    protected boolean _visible = true;
    protected String _friendlyName;
    protected final Map<Long, MapItem> _items = new ConcurrentHashMap<>();
    protected final Map<Long, MapGroup> _groups = new ConcurrentHashMap<>();

    @Override
    protected boolean removeItemImpl(MapItem item) {
        MapItem mi = _items.remove(item.getSerialId());
        return (mi != null);
    }

    @Override
    public Collection<MapGroup> getChildGroups() {
        return Collections.unmodifiableCollection(_groups.values());
    }

    public Collection<MapGroup> getMapGroups() {
        return Collections.unmodifiableCollection(_groups.values());
    }

    @Override
    public Collection<MapItem> getItems() {
        return Collections.unmodifiableCollection(_items.values());
    }

    public Collection<MapItem> getMapItems() {
        return Collections.unmodifiableCollection(_items.values());
    }

    @Override
    public MapGroup getChildGroupById(long id) {
        return _groups.get(id);
    }

    @Override
    public MapItem getItemById(long id) {
        // Log.d("DefaultMapGroup", "Looking up item " + id + " in " + _items + " of " +
        // getFriendlyName());
        return _items.get(id);
    }

    @Override
    public boolean containsItem(MapItem item) {
        return item != null && item == _items.get(item.getSerialId());
    }

    @Override
    public boolean deepForEachItem(MapItemsCallback cb) {
        return MapGroup.deepMapItems(this, cb);
    }

    @Override
    public boolean forEachItem(MapItemsCallback cb) {
        return MapGroup.mapItems(this, cb);
    }

    @Override
    public MapItem findClosestItem(GeoPoint location, double threshold,
            Map<String, String> metadata) {

        MapItem candidate = null;
        double candidateDistance = Double.NaN;

        MetaDataMapItemFilter filter = new MetaDataMapItemFilter(metadata);
        double distance;
        for (MapItem i : _items.values()) {
            distance = MapItem.computeDistance(i, location);
            if (Double.isNaN(distance) || distance > threshold)
                continue;
            if (!Double.isNaN(candidateDistance)
                    && distance >= candidateDistance)
                continue;
            if (!filter.onItemFunction(i))
                continue;
            candidate = i;
            candidateDistance = distance;
        }
        return candidate;
    }

    @Override
    public Collection<MapItem> findItems(final GeoPoint location,
            final double radius,
            final Map<String, String> metadata) {

        final Collection<MapItem> candidates = new LinkedList<>();

        if (location != null) {
            final MetaDataMapItemFilter filter = new MetaDataMapItemFilter(
                    metadata);
            double distance;
            for (MapItem i : _items.values()) {
                distance = MapItem.computeDistance(i, location);
                if (Double.isNaN(distance) || distance > radius)
                    continue;
                if (!filter.onItemFunction(i))
                    continue;
                candidates.add(i);
            }
        }
        return candidates;
    }

    public Collection<MapItem> findItems(GeoBounds bounds,
            Map<String, String> metadata) {

        FOVFilter boundsfilter = bounds == null ? null : new FOVFilter(bounds);
        MetaDataMapItemFilter metafilter = (metadata == null
                || metadata.size() < 1)
                        ? null
                        : new MetaDataMapItemFilter(metadata);

        Collection<MapItem> candidates = new LinkedList<>();
        for (MapItem i : _items.values()) {
            if (boundsfilter != null && !boundsfilter.accept(i))
                continue;

            if (metafilter != null && !metafilter.onItemFunction(i))
                continue;

            candidates.add(i);
        }

        return candidates;
    }

    @Override
    public Collection<MapItem> deepFindItems(GeoBounds bounds,
            Map<String, String> metadata) {

        LinkedList<MapItem> retval = new LinkedList<>(
                this.findItems(bounds, metadata));

        Collection<MapGroup> children = this.getChildGroups();
        for (MapGroup child : children)
            retval.addAll(child.deepFindItems(bounds, metadata));

        return retval;
    }

    /**************************************************************************/

    public static class MetaDataMapItemFilter implements
            MapGroup.MapItemsCallback {
        protected final Set<Map.Entry<String, String>> metadata;

        public MetaDataMapItemFilter(Map<String, String> metadata) {
            this.metadata = (metadata != null) ? metadata.entrySet() : null;
        }

        @Override
        public boolean onItemFunction(MapItem item) {
            if (metadata != null) {
                for (Map.Entry<String, String> entry : this.metadata) {
                    // XXX - wildcards
                    if (!Objects.equals(entry.getValue(),
                            item.getMetaString(entry.getKey(), null)))
                        return false;
                }
            }
            return true;
        }

    }

}
