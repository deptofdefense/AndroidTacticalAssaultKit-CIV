
package com.atakmap.android.maps;

import com.atakmap.android.maps.hittest.DeepHitTestQuery;
import com.atakmap.android.maps.hittest.RootHitTestQuery;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.hittest.HitTestQueryParameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public final class RootMapGroup extends DefaultMapGroup
        implements DeepHitTestQuery {

    private final static boolean DEBUGGING = false;

    private final static String TAG = "RootMapGroup";

    private final Map<DeepMapItemQuery, DeepQuerySpec> deepQueryFunctions;
    private final FastUIDLookup uidIndex;
    private final RootHitTestQuery hitTester;

    RootMapGroup() {
        super("Root");

        this.deepQueryFunctions = new IdentityHashMap<>();
        this.uidIndex = new FastUIDLookup();
        this.hitTester = new RootHitTestQuery();
    }

    FastUIDLookup getUidIndex() {
        return this.uidIndex;
    }

    synchronized void dispose() {
        this._groups.clear();
        this._items.clear();
    }

    /************************************************************************/
    // DEEP QUERY FUNCTIONS

    /**
     * Stock implementation of searching for a UID.
     */
    @Override
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "@Nullable", "public"
    })
    public synchronized MapItem deepFindUID(final String uid) {
        final MapItem byUid = this.uidIndex.get(uid);
        if (byUid != null)
            return byUid;
        return deepFindItemImpl(Collections.singletonMap("uid", uid), true);
    }

    @Override
    public synchronized final MapItem deepFindClosestItem(GeoPoint location,
            double threshold,
            Map<String, String> metadata) {

        SortedMap<Double, MapItem> candidates = new TreeMap<>();

        MapItem result;
        double distance;

        // XXX - MapItem.computeDistance is not well-defined over future
        // derivatives of MapItem

        result = this.findClosestItem(location, threshold, metadata);
        if (result != null) {
            distance = MapItem.computeDistance(result, location);
            if (!Double.isNaN(distance))
                candidates.put(distance, result);
        }

        for (DeepMapItemQuery query : this.deepQueryFunctions.keySet()) {
            try {
                result = query.deepFindClosestItem(location, threshold,
                        metadata);
                if (result != null) {
                    distance = MapItem.computeDistance(result, location);
                    if (!Double.isNaN(distance))
                        candidates.put(distance, result);
                }
            } catch (RuntimeException e) {
                Log.e(TAG,
                        query.getClass().getSimpleName() + ": " + e.getClass());
                if (DEBUGGING)
                    throw e;
            }
        }

        if (candidates.size() < 1)
            return null;

        return candidates.get(candidates.firstKey());
    }

    @Override
    public synchronized final Collection<MapItem> deepFindItems(
            GeoPoint location, double radius,
            Map<String, String> metadata) {
        List<MapItem> retval = new LinkedList<>(
                this.findItems(location, radius, metadata));
        for (DeepMapItemQuery query : this.deepQueryFunctions.keySet())
            try {
                retval.addAll(query.deepFindItems(location, radius, metadata));
            } catch (RuntimeException e) {
                Log.e(TAG,
                        query.getClass().getSimpleName() + ": " + e.getClass());
                if (DEBUGGING)
                    throw e;
            }
        return retval;
    }

    @Override
    public SortedSet<MapItem> deepHitTest(MapView mapView,
            HitTestQueryParameters params) {
        final SortedSet<MapItem> candidates = new TreeSet<>(
                MapItem.ZORDER_HITTEST_COMPARATOR);

        // Only map item results supported
        if (!params.acceptsResult(MapItem.class))
            return null;

        // Separate hit test queries from legacy map item queries
        List<DeepMapItemQuery> legacyQueries = new ArrayList<>();
        List<DeepHitTestQuery> hitQueries = new ArrayList<>();
        for (DeepMapItemQuery query : this.deepQueryFunctions.keySet()) {
            if (query instanceof DeepHitTestQuery)
                hitQueries.add((DeepHitTestQuery) query);
            else
                legacyQueries.add(query);
        }

        SortedSet<MapItem> result;

        // GL-based hit-testing
        result = hitTester.deepHitTest(mapView, params, hitQueries);
        if (result != null)
            candidates.addAll(result);

        int xpos = (int) params.point.x;
        int ypos = (int) params.point.y;

        // Geodetic hit-testing (slow, deprecated)
        if (!params.hitLimit(candidates)) {
            result = this.hitTestItems(xpos, ypos, params.geo, mapView);
            if (result != null)
                candidates.addAll(result);
        }

        // Run legacy hit test queries
        for (DeepMapItemQuery query : legacyQueries) {
            if (params.hitLimit(candidates))
                break;
            try {
                result = query.deepHitTestItems(xpos, ypos, params.geo,
                        mapView);
                if (result != null)
                    candidates.addAll(result);
            } catch (RuntimeException e) {
                Log.e(TAG,
                        query.getClass().getSimpleName() + ": " + e.getClass());
                if (DEBUGGING)
                    throw e;
            }
        }

        // Return results
        return candidates.isEmpty() ? null : candidates;
    }

    @Override
    public synchronized final MapItem deepHitTest(final int xpos,
            final int ypos, final GeoPoint point, final MapView view) {
        SortedSet<MapItem> candidates = new TreeSet<>(
                MapItem.ZORDER_HITTEST_COMPARATOR);

        MapItem result;

        // Geodetic hit-testing (slow, deprecated)
        result = this.hitTest(xpos, ypos, point, view);
        if (result != null)
            candidates.add(result);

        // Other map item queries
        for (DeepMapItemQuery query : this.deepQueryFunctions.keySet()) {
            try {
                result = query.deepHitTest(xpos, ypos, point, view);
                if (result != null)
                    candidates.add(result);
            } catch (RuntimeException e) {
                Log.e(TAG,
                        query.getClass().getSimpleName() + ": " + e.getClass());
                if (DEBUGGING)
                    throw e;
            }
        }

        if (candidates.size() > 0)
            return candidates.first();
        else
            return null;
    }

    @Override
    public synchronized final MapItem deepFindItem(
            Map<String, String> metadata) {
        return this.deepFindItemImpl(metadata, false);
    }

    private MapItem deepFindItemImpl(Map<String, String> metadata,
            boolean ignoreUidIndex) {
        MapItem result;

        final String uid = metadata.get("uid");
        final boolean isUIDOnlyQuery = (uid != null) && (metadata.size() == 1);

        // if metadata includes UID, try to use the index
        if (!ignoreUidIndex && uid != null) {
            result = this.uidIndex.get(uid);
            if (result != null) {
                // only looking for UID, return immediately
                if (isUIDOnlyQuery)
                    return result;

                // make sure remainder of metadata matches
                if (MapGroup.matchItemWithMetaString(result, metadata))
                    return result;

                // XXX - fall through appropriate???
            }
        }

        result = this.findItem(metadata);
        if (result != null)
            return result;

        DeepMapItemQuery query;
        for (Map.Entry<DeepMapItemQuery, DeepQuerySpec> entry : this.deepQueryFunctions
                .entrySet()) {

            // XXX - pretty bad, but not any worse that the capability being
            //       completely regressed
            if (isUIDOnlyQuery && entry.getValue().identity)
                continue;

            query = entry.getKey();
            try {
                result = query.deepFindItem(metadata);
                if (result != null)
                    return result;
            } catch (RuntimeException e) {
                Log.e(TAG,
                        query.getClass().getSimpleName() + ": " + e.getClass());
                if (DEBUGGING)
                    throw e;
            }
        }
        return null;
    }

    /**
     * Get all items with a registered UID
     * @return List of all map items on the map
     */
    public synchronized Collection<MapItem> getAllItems() {
        return uidIndex.getItems();
    }

    /*************************************************************************/
    // Default Map Group

    @Override
    public final MapGroup addGroup(MapGroup groupIface) {
        return this.addGroup(groupIface, null);
    }

    public final MapGroup addGroup(MapGroup groupIface,
            DeepMapItemQuery query) {
        return this.addGroupImpl(groupIface, query);
    }

    private synchronized MapGroup addGroupImpl(MapGroup groupIface,
            DeepMapItemQuery query) {
        final MapGroup retval = super.addGroup(groupIface);
        if (query == null)
            query = groupIface;
        DeepQuerySpec spec = this.deepQueryFunctions.get(query);
        if (spec == null)
            this.deepQueryFunctions
                    .put(
                            query,
                            spec = new DeepQuerySpec(query == groupIface));
        spec.targets.add(groupIface);
        return retval;
    }

    @Override
    public final MapGroup addGroup() {
        return this.addGroup(new DefaultMapGroup());
    }

    @Override
    public final MapGroup addGroup(String friendlyName) {
        return this.addGroup(new DefaultMapGroup(friendlyName));
    }

    @Override
    public synchronized final boolean removeGroupImpl(MapGroup group,
            boolean referenceOnly) {
        final boolean retval = super.removeGroupImpl(group, referenceOnly);
        if (retval) {
            Iterator<DeepQuerySpec> iter = this.deepQueryFunctions.values()
                    .iterator();
            DeepQuerySpec spec;
            while (iter.hasNext()) {
                spec = iter.next();
                spec.targets.remove(group);
                if (spec.targets.isEmpty())
                    iter.remove();
            }
        }
        return retval;
    }

    /**************************************************************************/

    private static class DeepQuerySpec {
        public final boolean identity;
        public final Set<MapGroup> targets;

        public DeepQuerySpec(boolean identity) {
            this(identity, null);
        }

        public DeepQuerySpec(boolean identity, MapGroup target) {
            this.identity = identity;
            this.targets = Collections
                    .newSetFromMap(new IdentityHashMap<MapGroup, Boolean>());
            if (target != null)
                this.targets.add(target);
        }
    }

    /**************************************************************************/

    // XXX - generic FastLookup for arbitrary metadata, signaled on
    //       ITEM_REFRESHED event

    /**
     * Keeps a cache of all items that have been added to the map for fast UID lookup.
     */
    public static class FastUIDLookup implements
            MapEventDispatcher.MapEventDispatchListener {
        final Map<String, MapItem> map = new HashMap<>();

        @Override
        public void onMapEvent(final MapEvent event) {
            final String etype = event.getType();
            final MapItem mi = event.getItem();
            final String uid = mi.getUID();
            final boolean added = etype.equals(MapEvent.ITEM_ADDED);
            final boolean removed = etype.equals(MapEvent.ITEM_REMOVED);

            // See ATAK-10160
            // Without synchronization some items do not get mapped properly
            // Map events can be dispatched from any thread
            synchronized (this) {
                if (added)
                    map.put(uid, mi);
                else if (removed)
                    map.remove(uid);
            }
        }

        /**
         * Retrieves a map item based on a supplied UID.
         * @return null if there is no map item.
         */
        public MapItem get(String uid) {
            // No need to synchronize get - this seems to work as expected
            return map.get(uid);
        }

        public synchronized List<MapItem> getItems() {
            return new ArrayList<>(map.values());
        }
    }
}
