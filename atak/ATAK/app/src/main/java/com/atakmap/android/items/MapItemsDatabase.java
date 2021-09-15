
package com.atakmap.android.items;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MetaDataHolder;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.FilteredRowIterator;
import com.atakmap.database.RowIterator;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public abstract class MapItemsDatabase {

    protected final Set<ContentListener> contentListeners;

    protected MapItemsDatabase() {
        this.contentListeners = Collections
                .newSetFromMap(new IdentityHashMap<ContentListener, Boolean>());
    }

    /**************************************************************************/

    public abstract void close();

    /**
     * Returns a flag indicating whether or not the database is read-only.
     * 
     * @return <code>true</code> if the database is read-only, <code>false</code> otherwise.
     */
    public abstract boolean isReadOnly();

    protected void checkReadOnly() {
        if (this.isReadOnly())
            throw new UnsupportedOperationException("Database is read-only.");
    }

    /**************************************************************************/
    // Group Queries

    /**
     * Returns the group with the specified serial ID.
     * 
     * @param serialId The serial ID of the group.
     * @return The group with the specified serial ID or <code>null</code> in the case that no group
     *         with that serial ID is present.
     */
    public abstract MapGroup getGroup(long serialId);

    /**
     * Returns the child groups of the specified group. If <code>0</code> is specified, the root
     * groups available from the database are returned.
     * 
     * @param parentGroupSerialId The serial ID of a group. If <code>0</code> is specified, the root
     *            groups available from this database are returned.
     * @return The child groups (immediate descendants) of the specified group.
     */
    public abstract MetaDataHolderCursor<MapGroup> getGroups(
            long parentGroupSerialId);

    /**
     * Returns the parent group of the specified group.
     * 
     * @param childSerialId The group serial ID
     * @return The parent of the specified group or <code>null</code> if the specified group does
     *         not have a parent.
     */
    public abstract MapGroup getParentGroup(long childSerialId);

    /**
     * Returns the visible property of the specified group.
     * 
     * @param serialId The group serial ID
     * @return The visible property of the specified group.
     */
    public abstract boolean getGroupVisible(long serialId);

    public void insertGroup(MapGroup groupToAdd, boolean rootGroup) {
        this.checkReadOnly();

        this.insertGroupImpl(groupToAdd, rootGroup);

        this.dispatchContentChanged();
    }

    /**
     * Inserts the specified group. The parent that the group should be added to can be derived by
     * invoking {@link MapGroup#getParentGroup()} on <code>groupToAdd</code>.
     * 
     * @param groupToAdd The group to be added.
     */
    protected abstract void insertGroupImpl(MapGroup groupToAdd,
            boolean rootGroup);

    public MapGroup insertGroup(long parentSerialId, String friendlyName,
            boolean rootGroup) {
        this.checkReadOnly();

        final MapGroup retval = this.insertGroupImpl(parentSerialId,
                friendlyName, rootGroup);

        this.dispatchContentChanged();

        return retval;
    }

    /**
     * Inserts the specified group.
     * 
     * @param parentSerialId The serial ID for the parent that the new group will be added to
     * @param friendlyName The friendly name of the new group
     * @return The group that was newly added.
     */
    protected abstract MapGroup insertGroupImpl(long parentSerialId,
            String friendlyName,
            boolean rootGroup);

    public void deleteChildGroups(long serialId) {
        this.checkReadOnly();

        this.deleteChildGroupsImpl(serialId);

        this.dispatchContentChanged();
    }

    /**
     * Deletes all child groups of a group.
     * 
     * @param serialId The group serial ID.
     */
    protected abstract void deleteChildGroupsImpl(long serialId);

    public void deleteGroup(long serialId) {
        this.checkReadOnly();

        this.deleteGroupImpl(serialId);

        this.dispatchContentChanged();
    }

    /**
     * Deletes the specified group. All subgroups and items will also be deleted.
     * 
     * @param serialId The serial ID of the group
     */
    protected abstract void deleteGroupImpl(long serialId);

    public void updateGroup(long serialId, String friendlyName) {
        this.checkReadOnly();

        this.updateGroupImpl(serialId, friendlyName);
    }

    /**
     * Updates the friendly name of a group.
     * 
     * @param serialId The group serial ID
     * @param friendlyName The new friendly name for the group.
     */
    protected abstract void updateGroupImpl(long serialId, String friendlyName);

    /**
     * Updates the visibility property of a group.
     * 
     * @param serialId The group serial ID
     * @param visible The new value for the visible state of the group.
     */
    protected abstract void updateGroup(long serialId, boolean visible);

    /**************************************************************************/
    // Item Queries

    public void insertItem(long parentGroupSerialId, MapItem item) {
        this.checkReadOnly();

        this.insertItemImpl(parentGroupSerialId, item);

        this.dispatchContentChanged();
    }

    /**
     * Insert the specified item into the specified group.
     * 
     * @param parentGroupSerialId The serial ID of the parent group for the item.
     * @param item The item to be inserted.
     */
    protected abstract void insertItemImpl(long parentGroupSerialId,
            MapItem item);

    public void deleteItems(long parentGroupSerialId) {
        this.checkReadOnly();

        this.deleteItemsImpl(parentGroupSerialId);

        this.dispatchContentChanged();
    }

    public void deleteItem(long serialId) {
        this.checkReadOnly();

        this.deleteItemImpl(serialId);

        this.dispatchContentChanged();
    }

    /**
     * Deletes all items for a specified group; not recursive.
     * 
     * @param parentGroupSerialId The group serial ID
     */
    protected abstract void deleteItemsImpl(long parentGroupSerialId);

    /**
     * @param serialId Item serial ID
     */
    protected abstract void deleteItemImpl(long serialId);

    /**
     * @param serialId The item serial ID
     * @return The item with the associated serial ID
     */
    public abstract MapItem getItem(long serialId);

    public MetaDataHolderCursor<MapItem> queryItems(String key, String value) {
        return this.queryItems(key, value, false);
    }

    public MetaDataHolderCursor<MapItem> queryItems(String key, String value,
            boolean visibleOnly) {
        return this.queryItems(null, null, Double.NaN, key, value, visibleOnly);
    }

    public MetaDataHolderCursor<MapItem> queryItems(
            Map<String, String> metadata) {
        return this.queryItems(metadata, false);
    }

    public MetaDataHolderCursor<MapItem> queryItems(
            Map<String, String> metadata,
            boolean visibleOnly) {
        return this.queryItems(null, null, Double.NaN, metadata, visibleOnly);
    }

    public MetaDataHolderCursor<MapItem> queryItems(GeoPoint upperLeft,
            GeoPoint lowerRight,
            String key, String value) {
        return this.queryItems(upperLeft, lowerRight, key, value, false);
    }

    public MetaDataHolderCursor<MapItem> queryItems(GeoPoint upperLeft,
            GeoPoint lowerRight,
            String key, String value, boolean visibleOnly) {
        return this.queryItems(upperLeft, lowerRight, Double.NaN, key, value,
                visibleOnly);
    }

    public MetaDataHolderCursor<MapItem> queryItems(GeoPoint upperLeft,
            GeoPoint lowerRight,
            Map<String, String> metadata) {
        return this.queryItems(upperLeft, lowerRight, metadata, false);
    }

    public MetaDataHolderCursor<MapItem> queryItems(GeoPoint upperLeft,
            GeoPoint lowerRight,
            Map<String, String> metadata, boolean visibleOnly) {
        return this.queryItems(upperLeft, lowerRight, Double.NaN, metadata,
                visibleOnly);
    }

    public MetaDataHolderCursor<MapItem> queryItems(GeoPoint upperLeft,
            GeoPoint lowerRight,
            double mapScale) {
        return this.queryItems(upperLeft, lowerRight, mapScale, false);
    }

    public MetaDataHolderCursor<MapItem> queryItems(GeoPoint upperLeft,
            GeoPoint lowerRight,
            double mapScale, boolean visibleOnly) {
        return this.queryItems(upperLeft, lowerRight, mapScale,
                Collections.<String, String> emptyMap(), visibleOnly);
    }

    public MetaDataHolderCursor<MapItem> queryItems(GeoPoint upperLeft,
            GeoPoint lowerRight,
            double mapScale, String key, String value) {
        return this.queryItems(upperLeft, lowerRight, mapScale, key, value,
                false);
    }

    public MetaDataHolderCursor<MapItem> queryItems(GeoPoint upperLeft,
            GeoPoint lowerRight,
            double mapScale, String key, String value, boolean visibleOnly) {
        Map<String, String> metadata;
        if (key != null)
            metadata = Collections.singletonMap(key, value);
        else
            metadata = Collections.emptyMap();

        return this.queryItems(upperLeft, lowerRight, mapScale, metadata,
                visibleOnly);
    }

    public MetaDataHolderCursor<MapItem> queryItems(GeoPoint upperLeft,
            GeoPoint lowerRight,
            double mapScale, Map<String, String> metadata) {
        return this
                .queryItems(upperLeft, lowerRight, mapScale, metadata, false);
    }

    public MetaDataHolderCursor<MapItem> queryItems(GeoPoint upperLeft,
            GeoPoint lowerRight,
            double mapScale, Map<String, String> metadata,
            boolean visibleOnly) {
        return this.queryItems(0L, true, upperLeft, lowerRight, mapScale,
                metadata, visibleOnly);
    }

    public MetaDataHolderCursor<MapItem> queryItems(long parentGroupSerialId,
            boolean recurse,
            GeoPoint upperLeft, GeoPoint lowerRight, double mapScale,
            Map<String, String> metadata,
            boolean visibleOnly) {
        return this.queryItemsImpl(parentGroupSerialId, recurse, upperLeft,
                lowerRight, mapScale,
                metadata, visibleOnly);
    }

    public MetaDataHolderCursor<MapItem> queryItems(long parentGroupSerialId,
            boolean recurse,
            GeoPoint center, double radius, double mapScale,
            Map<String, String> metadata,
            boolean visibleOnly) {
        return this.queryItemsImpl(parentGroupSerialId, recurse, center,
                radius, mapScale,
                metadata, visibleOnly);
    }

    /**
     * @param parentGroupSerialId The serial ID for the top-most group to include in the query. If
     *            <code>0</code> is specified, a deep search over all groups may be performed by
     *            specifying <code>recurse</code> as <code>true</code>
     * @param recurse <code>true</code> to perform a recursive query, <code>false</code> to limit
     *            only to the specified group.
     * @param upperLeft The upper-left bound of the region of interest for the query. If both
     *            <code>upperLeft</code> and <code>lowerRight</code> are <code>null</code>, the
     *            query will not be restricted to a geographic extent.
     * @param lowerRight The lower-right bound of the region of interest for the query. If both
     *            <code>upperLeft</code> and <code>lowerRight</code> are <code>null</code>, the
     *            query will not be restricted to a geographic extent.
     * @param mapScale The map scale for the query. Only items that should be displayed at the
     *            specified scale should be included in the results. If specified as
     *            {@link Double#NaN}, the query should not be constrained by scale.
     * @param metadata The metadata for the query. If non-empty, only items with matching metadata
     *            should be included in the results.
     * @param visibleOnly If <code>true</code> only items that are currently visible should be
     *            included in the results.
     * @return The query result.
     */
    protected abstract MetaDataHolderCursor<MapItem> queryItemsImpl(
            long parentGroupSerialId,
            boolean recurse, GeoPoint upperLeft, GeoPoint lowerRight,
            double mapScale,
            Map<String, String> metadata, boolean visibleOnly);

    /**
     * Queries items about a point within a specified radius.
     * <P>
     * The default implementation performs a query about a radius by performing a bounding box query
     * and excluding all results with a distance greater than <code>radius</code> from
     * <code>center</code>. This method relies on the implementations of
     * {@link #computeDistance(MapItem, GeoPoint)}. The default
     * implementation is inefficient and should be replaced as the underlying database allows.
     * 
     * @param parentGroupSerialId The serial ID for the top-most group to include in the query. If
     *            <code>0</code> is specified, a deep search over all groups may be performed by
     *            specifying <code>recurse</code> as <code>true</code>
     * @param recurse <code>true</code> to perform a recursive query, <code>false</code> to limit
     *            only to the specified group.
     * @param center The center point for the query's region of interest. May not be
     *            <code>null</code>.
     * @param radius The radius of the query's region of interest specified in meters.
     * @param mapScale The map scale for the query. Only items that should be displayed at the
     *            specified scale should be included in the results. If specified as
     *            {@link Double#NaN}, the query should not be constrained by scale.
     * @param metadata The metadata for the query. If non-empty, only items with matching metadata
     *            should be included in the results.
     * @param visibleOnly If <code>true</code> only items that are currently visible should be
     *            included in the results.
     * @return The query result.
     */
    protected MetaDataHolderCursor<MapItem> queryItemsImpl(
            long parentGroupSerialId,
            boolean recurse, final GeoPoint center, final double radius,
            double mapScale,
            Map<String, String> metadata, boolean visibleOnly) {
        final double distance = Math.sqrt(2.0d) * radius;
        final GeoPoint upperLeft = GeoCalculations.pointAtDistance(center,
                315.0d, distance);
        final GeoPoint lowerRight = GeoCalculations.pointAtDistance(center,
                135.0d, distance);

        return new FilteredMetaDataHolderCursor<MapItem>(
                this.queryItemsImpl(parentGroupSerialId,
                        recurse, upperLeft, lowerRight, mapScale, metadata,
                        visibleOnly)) {
            @Override
            protected boolean accept() {
                final double itemDistance = MapItemsDatabase.this
                        .computeDistance(this.get(),
                                center);

                return !Double.isNaN(itemDistance) && (itemDistance < radius);
            }
        };
    }

    /**
     * Finds the item closest to the specified point. The default implementation performs a
     * brute-force search.
     * 
     * @param parentGroupSerialId The serial ID for the top-most group to include in the query. If
     *            <code>0</code> is specified, a deep search over all groups may be performed by
     *            specifying <code>recurse</code> as <code>true</code>
     * @param recurse <code>true</code> to perform a recursive query, <code>false</code> to limit
     *            only to the specified group.
     * @param location The location for the query. May not be' <code>null</code>.
     * @param mapScale The map scale for the query. Only items that should be displayed at the
     *            specified scale should be included in the results. If specified as
     *            {@link Double#NaN}, the query should not be constrained by scale.
     * @param metadata The metadata for the query. If non-empty, only items with matching metadata
     *            should be included in the results.
     * @param visibleOnly If <code>true</code> only items that are currently visible should be
     *            included in the results.
     * @return The query result.
     */
    public MapItem queryClosestItem(long parentGroupSerialId, boolean recurse,
            GeoPoint location,
            double mapScale, Map<String, String> metadata,
            boolean visibleOnly) {
        MetaDataHolderCursor<MapItem> result = null;
        try {
            result = this.queryItems(parentGroupSerialId, recurse, null, null,
                    mapScale, metadata,
                    visibleOnly);

            double candidateDistance = Double.NaN;
            MapItem candidate = null;
            double itemDistance;
            MapItem item = null;
            while (result.moveToNext()) {
                item = result.get();
                itemDistance = this.computeDistance(item, location);
                if (!Double.isNaN(itemDistance)
                        && (candidate == null
                                || itemDistance < candidateDistance)) {
                    candidate = item;
                    candidateDistance = itemDistance;
                }
            }

            return candidate;
        } finally {
            if (result != null)
                result.close();
        }
    }

    public double computeDistance(MapItem databaseMapItem, GeoPoint point) {
        return MapItem.computeDistance(databaseMapItem, point);
    }

    /**************************************************************************/
    //

    public synchronized void addContentListener(ContentListener l) {
        this.contentListeners.add(l);
    }

    public synchronized void removeContentListener(ContentListener l) {
        this.contentListeners.remove(l);
    }

    protected synchronized void dispatchContentChanged() {
        this.dispatchContentChangedNoSync();
    }

    protected void dispatchContentChangedNoSync() {
        for (ContentListener l : this.contentListeners)
            l.contentChanged(this);
    }

    /**************************************************************************/

    public interface MetaDataHolderCursor<T extends MetaDataHolder>
            extends RowIterator {
        T get();
    }

    public static abstract class FilteredMetaDataHolderCursor<T extends MetaDataHolder>
            extends
            FilteredRowIterator implements MetaDataHolderCursor<T> {

        public FilteredMetaDataHolderCursor(MetaDataHolderCursor<T> cursor) {
            super(cursor);
        }

        @Override
        public T get() {
            return ((MetaDataHolderCursor<T>) this.filter).get();
        }
    }

    public interface ContentListener {
        void contentChanged(MapItemsDatabase database);
    }
}
