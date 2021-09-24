
package com.atakmap.android.hierarchy.items;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.atakmap.android.features.FeatureSetHierarchyListItem;
import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.HashtagSearch;
import com.atakmap.android.hashtags.util.HashtagUtils;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem2;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.GroupDelete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.filters.EmptyListFilter;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.hierarchy.filters.MultiFilter;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.FeatureDataStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractHierarchyListItem2 implements
        HierarchyListItem2, HashtagSearch {

    private static final String TAG = "AbstractHierarchyListItem2";
    private static final ExecutorService refreshQueue = Executors
            .newFixedThreadPool(10, new NamedThreadFactory(
                    "RefreshPool"));

    // Resource leak testing
    /*private static final SparseArray<AbstractHierarchyListItem2> _test
            = new SparseArray<AbstractHierarchyListItem2>();
    
    public static void listListeners(String tag) {
        synchronized (_test) {
            for (int i = 0; i < _test.size(); i++) {
                AbstractHierarchyListItem2 item = _test.valueAt(i);
                AbstractHierarchyListItem2 item = _test.valueAt(i);
                if (item != null)
                    Log.e(tag, item.getTitle() + " (" + item.getClass() + ")");
                else
                    Log.e(tag, "null item at " + i);
            }
        }
    }*/

    private final Map<String, Object> localData = new HashMap<>();

    // Common fields
    protected final List<HierarchyListItem> children = new ArrayList<>();
    protected HierarchyListFilter filter;
    protected HierarchyListFilter postFilter = new EmptyListFilter();
    protected BaseAdapter listener;

    // True to refresh this list using the thread pool
    // False to run refresh on the UI thread (not recommended)
    protected boolean asyncRefresh = false;

    // True if this list has been disposed (i.e. no longer usable)
    protected boolean disposed = false;

    // True to reuse this list after disposal (disposed will always remain false)
    protected boolean reusable = false;

    protected final Handler uiHandler = new Handler(Looper.getMainLooper());

    protected AbstractHierarchyListItem2() {
        /*synchronized (_test) {
            Log.w(TAG, "add: " + this
                    + " " + hashCode() + " (" + _test.size() + " existing)");
            _test.put(hashCode(), this);
        }*/
    }

    @Override
    public String getUID() {
        return getTitle();
    }

    @Override
    public int getPreferredListIndex() {
        return -1;
    }

    @Override
    public boolean isChildSupported() {
        return true;
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        List<HierarchyListItem> filtered = getChildren();
        if (index >= 0 && index < filtered.size())
            return filtered.get(index);
        return null;
    }

    @Override
    public int getChildCount() {
        return isChildSupported() ? getChildren().size() : 0;
    }

    /**
     * Used for post-filtering items after refresh has been done
     * Useful in situations where the results of the refresh may
     * influence the results of a secondary filtering process
     * @param item Item to filter
     * @return True if the item passes the filter
     */
    protected boolean postAccept(HierarchyListItem item) {
        //synchronized (this.children) {
        return postFilter == null || postFilter.accept(item);
        //}
    }

    /**
     * Set the post-filter
     */
    public void setPostFilter(HierarchyListFilter filter) {
        //synchronized (this.children) {
        postFilter = filter;
        //}
    }

    // Default overrides so we don't break compatibility with plugins

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public Drawable getIconDrawable() {
        // Return null to use getIconUri instead
        return null;
    }

    @Override
    public int getIconColor() {
        return Color.WHITE;
    }

    @Override
    public String getIconUri() {
        return null;
    }

    @Override
    public boolean isMultiSelectSupported() {
        return true;
    }

    @Override
    public String getAssociationKey() {
        return null;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    public View getExtraView(View convertView, ViewGroup parent) {
        return getExtraView();
    }

    @Override
    public View getCustomLayout() {
        return null;
    }

    @Override
    public View getHeaderView() {
        return null;
    }

    @Override
    public View getFooterView() {
        return null;
    }

    @Override
    public View getListItemView(View convertView, ViewGroup parent) {
        return null;
    }

    @Override
    public double[] getDropDownSize() {
        // -1 = default size
        return new double[] {
                -1, -1
        };
    }

    @Override
    public final Object setLocalData(String s, Object o) {
        return this.localData.put(s, o);
    }

    @Override
    public final Object getLocalData(String s) {
        return this.localData.get(s);
    }

    @Override
    public final <T> T getLocalData(String s, Class<T> clazz) {
        return clazz.cast(this.getLocalData(s));
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.isInstance(this))
            return clazz.cast(this);
        return null;
    }

    @Override
    public List<Sort> getSorts() {
        return getDefaultSortModes(this);
    }

    /**
     * Get list of sort modes based on supported actions
     * @param list List item
     * @return List of sort modes (classes)
     */
    public static List<Sort> getDefaultSortModes(HierarchyListItem list) {
        List<Sort> sortModes = new ArrayList<>();
        if (list == null || !list.isChildSupported())
            return sortModes;

        // Alphabetic is always supported by default
        sortModes.add(new SortAlphabet());

        // Check if distance sort is supported
        MapView mv = MapView.getMapView();
        GeoPoint gp = null;
        if (mv != null) {
            Marker self = ATAKUtilities.findSelf(mv);
            gp = self != null ? self.getPoint() : mv.getPoint().get();
        }
        SortDistanceFrom distSort = new SortDistanceFrom(gp);
        if (list instanceof FeatureSetHierarchyListItem) {
            // XXX - avoid distance sort if there are a lot of children
            if (list.getChildCount() < FeatureSetHierarchyListItem.MAX_DISTANCE_SORT)
                sortModes.add(distSort);
            return sortModes;
        }
        List<HierarchyListItem> children;
        if (list instanceof AbstractHierarchyListItem2)
            children = ((AbstractHierarchyListItem2) list).getChildren();
        else {
            children = new ArrayList<>();
            int count = list.getChildCount();
            for (int i = 0; i < count; i++) {
                HierarchyListItem item = list.getChildAt(i);
                if (item != null)
                    children.add(item);
            }
        }
        for (HierarchyListItem item : children) {
            if (item == null)
                continue;
            if (item instanceof ILocation || item instanceof MapItemUser) {
                sortModes.add(distSort);
                break;
            }
        }
        return sortModes;
    }

    /**
     * Get actions of a certain type from all children items
     * @param clazz Action class
     * @param <T> Action type
     * @return List of actions
     */
    public <T extends Action> List<T> getChildActions(Class<T> clazz) {
        List<HierarchyListItem> children = getChildren();
        List<T> ret = new ArrayList<>();
        for (HierarchyListItem item : children) {
            Action act = item.getAction(clazz);
            if (act != null)
                ret.add(clazz.cast(act));
        }
        return ret;
    }

    /**
     * Typical calculation for determining ternary visibility
     * Note that this class does not implement Visibility or Visibility2
     * @return VISIBLE if all children are completely visible
     *         INVISIBLE if all children are completely invisible
     *         SEMI_VISIBLE otherwise
     */
    public int getVisibility() {
        List<HierarchyListItem> children = getChildren();
        boolean partial = false;
        boolean all = true;
        for (HierarchyListItem item : children) {
            Visibility viz = item.getAction(Visibility.class);
            Visibility2 viz2 = item.getAction(Visibility2.class);

            // Ignore non-applicable items
            if (viz == null && viz2 == null)
                continue;

            if (viz2 != null) {
                int state = viz2.getVisibility();
                partial |= (state != Visibility2.INVISIBLE);
                all &= (state == Visibility2.VISIBLE);
            } else {
                boolean visible = viz.isVisible();
                partial |= visible;
                all &= visible;
            }
        }
        return partial && all ? Visibility2.VISIBLE
                : (partial
                        ? Visibility2.SEMI_VISIBLE
                        : Visibility2.INVISIBLE);
    }

    public boolean isVisible() {
        return getVisibility() != Visibility2.INVISIBLE;
    }

    public boolean setVisible(boolean visible) {
        List<Visibility> actions = getChildActions(Visibility.class);
        boolean ret = !actions.isEmpty();
        for (Visibility viz : actions)
            ret &= viz.setVisible(visible);
        return ret;
    }

    /**
     * Execute all delete actions defined in this class
     * @return True if all deletes executed successfully
     */
    public boolean delete() {
        // Get the list of delete actions
        List<Delete> actions = getDeleteActions();

        // Nothing to execute
        if (FileSystemUtils.isEmpty(actions))
            return true;

        // Execute delete actions and track success
        boolean ret = true;
        for (Delete delete : actions)
            ret &= delete.delete();

        return ret;
    }

    /**
     * Get the list of delete actions in this class
     *
     * Meant to serve as a default implementation of
     * {@link GroupDelete#getDeleteActions()}
     *
     * @return List of delete actions
     */
    public List<Delete> getDeleteActions() {
        return getChildActions(Delete.class);
    }

    public Set<HierarchyListItem> find(String terms) {
        Set<HierarchyListItem> ret = new HashSet<>();
        terms = terms.toLowerCase(LocaleUtil.getCurrent());
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            Search search = item.getAction(Search.class);
            if (search != null)
                ret.addAll(search.find(terms));
            String title = item.getTitle();
            if (title != null && title.toLowerCase(LocaleUtil.getCurrent())
                    .contains(terms))
                ret.add(item);
        }
        return ret;
    }

    /**
     * Send a refresh request to the thread pool
     * @param filter The new filter
     * @return The new filter object, in case it's changed
     */
    @Override
    public HierarchyListFilter refresh(final HierarchyListFilter filter) {
        if (filter != null) {
            this.filter = filter;
            postRefresh(new Runnable() {
                @Override
                public void run() {
                    if (isDisposed())
                        return;
                    try {
                        refreshImpl();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to run " + getTitle()
                                + " > refreshImpl", e);
                    }
                }
            });
        }
        return this.filter;
    }

    /**
     * Update this item's listener and refresh
     * @param listener New listener
     * @param filter New filter
     */
    public void refresh(BaseAdapter listener, HierarchyListFilter filter) {
        this.listener = listener;
        refresh(filter);
    }

    /**
     * Call a refresh synchronously, regardless of the asyncRefresh value
     * @param listener Listener
     * @param filter Refresh filter
     */
    public void syncRefresh(BaseAdapter listener, HierarchyListFilter filter) {
        this.listener = listener;
        this.filter = filter;
        refreshImpl();
    }

    /**
     * Request a refresh of Overlay Manager's current list
     */
    protected void requestRefresh() {
        if (this.listener instanceof HierarchyListAdapter)
            ((HierarchyListAdapter) this.listener).refreshList();
    }

    /**
     * Request a refresh of Overlay Manager's current list provided
     * the current path is directly above or below 'path'
     * @param path Backslash-delimited path of list UIDs
     */
    protected void requestRefresh(String path) {
        if (!(this.listener instanceof HierarchyListAdapter))
            return;
        HierarchyListAdapter om = (HierarchyListAdapter) this.listener;
        String curPath = om.getCurrentPath(null);
        if (curPath.startsWith(path) || path.startsWith(curPath))
            om.refreshList();
    }

    /**
     * Sort children based on filter's sort
     * To avoid UI thread slowdown avoid calling this
     * use sortItems on a separate list and copy to this.children instead
     * @deprecated This method will become final
     * @param sort Sort object (usually this.filter.sort)
     * @return The new sort object, in case it's changed
     */
    @Override
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = false)
    public Sort refresh(Sort sort) {
        Log.w(TAG,
                "Calling refresh(Sort) on this.children (could be slow; sort filtered copy instead)");
        synchronized (this.children) {
            sortItems(this.children);
        }
        return sort;
    }

    /**
     * Sort the list of items based on this.filter.sort
     * Unlike the function above, this requires no synchronization
     * (unless called on this.children, which isn't recommended)
     * @param items List of items
     */
    public void sortItems(List<HierarchyListItem> items) {
        if (this.filter == null || this.filter.sort == null)
            return;

        // Make sure sort type is a valid sort type for this list
        Sort sort = this.filter.sort;
        List<Sort> sorts = getSorts();
        if (!FileSystemUtils.isEmpty(sorts)) {
            boolean validSort = false;
            for (Sort s : sorts) {
                if (s.getClass().equals(sort.getClass())) {
                    validSort = true;
                    break;
                }
            }
            if (!validSort)
                sort = sorts.get(0);
        }

        // Sort items
        Comparator<HierarchyListItem> order;
        if (sort instanceof ComparatorSort) {
            ((ComparatorSort) sort).sort(items);
        } else {
            if (sort instanceof SortAlphabet)
                order = HierarchyListAdapter.MENU_ITEM_COMP;
            else if (sort instanceof SortAlphabetDesc)
                order = HierarchyListAdapter.MENU_ITEM_COMP_DESC;
            else
                order = new HierarchyListAdapter.ItemDistanceComparator(
                        (HierarchyListItem.SortDistanceFrom) sort);
            Collections.sort(items, order);
        }
    }

    /**
     * Copy a list of items to the children array on the UI thread
     * @param items List of filtered/sorted items
     */
    protected void updateChildren(final List<HierarchyListItem> items) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean sizeChanged = false;
                synchronized (children) {
                    if (!disposed) {
                        sizeChanged = children.size() != items.size();
                        if (reusable)
                            children.clear();
                        else
                            disposeChildren();
                        children.addAll(items);
                    } else {
                        // Refresh wasted, clean up
                        disposeItems(items);
                    }
                }
                notifyListener(sizeChanged);
            }
        });
    }

    /**
     * Return a copy of the children list (post-filtered)
     * @return List of children
     */
    public List<HierarchyListItem> getChildren() {
        if (!isChildSupported() || !isGetChildrenSupported())
            return new ArrayList<>();

        // XXX - this.children is purposefully not synchronized here
        // This method is called thousands of times during OM initialization
        // and synchronization appears to be significantly slower on certain
        // newer versions of Android which have this issue:
        // https://stackoverflow.com/questions/38810559/failed-lock-verification
        List<HierarchyListItem> children = new ArrayList<>(
                this.children);

        // Run post-filter on children items
        List<HierarchyListItem> filtered = new ArrayList<>();
        for (HierarchyListItem item : children) {
            if (item != null && postAccept(item))
                filtered.add(item);
        }
        return filtered;
    }

    /**
     * @return True if the above method is supported
     */
    public boolean isGetChildrenSupported() {
        return true;
    }

    /**
     * Find child based on matching UID
     * @param uid UID to search for
     * @return Matching item or null if not found
     */
    public HierarchyListItem findChild(String uid) {
        List<HierarchyListItem> filtered = getChildren();
        for (HierarchyListItem item : filtered) {
            if (item != null && uid.equals(item.getUID()))
                return item;
        }
        return null;
    }

    /**
     * Notify the listener adapter
     * If the adapter is part of Overlay Manager, include reference to self
     * so we know which item has been updated
     */
    protected void notifyListener(boolean sizeChanged) {
        if (this.listener != null) {
            if (this.listener instanceof HierarchyListAdapter)
                ((HierarchyListAdapter) this.listener)
                        .notifyDataSetChanged(this, sizeChanged);
            else
                this.listener.notifyDataSetChanged();
        } else
            Log.w(TAG, getTitle() + "<" + getClass().getSimpleName()
                    + ">.listener is NULL. This may cause desync problems.");
    }

    protected void notifyListener() {
        notifyListener(true);
    }

    /**
     * Submit runnable to refresh thread pool
     * @param r Runnable to submit to background thread
     */
    protected void postRefresh(Runnable r) {
        if (this.asyncRefresh) {
            async(r);
        } else {
            if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
                r.run();
            } else
                uiHandler.post(r);
        }
    }

    public static void async(Runnable r) {
        refreshQueue.submit(r);
    }

    @Override
    public void dispose() {
        if (!isChildSupported())
            return;
        synchronized (this.children) {
            if (reusable)
                this.children.clear();
            else {
                disposeChildren();
                this.disposed = true; // Prevent pending refreshes
            }

            /*synchronized (_test) {
                _test.remove(hashCode());
                Log.d(TAG, "del: " + getTitle()
                        + " " + hashCode() + " (" + _test.size() + " remaining)");
            }*/
        }
    }

    /**
     * Check whether this item has been disposed or not
     * @return True if the item has been disposed
     */
    public boolean isDisposed() {
        synchronized (this.children) {
            return this.disposed;
        }
    }

    /**
     * Call dispose on all children and clear
     */
    protected void disposeChildren() {
        synchronized (this.children) {
            disposeItems(this.children);
            this.children.clear();
        }
    }

    /**
     * Refresh implementation to be run on a background thread
     * If your class doesn't need to refresh then override the filter
     * method above to avoid unnecessary threading
     */
    protected abstract void refreshImpl();

    @Override
    public Collection<HashtagContent> search(Collection<String> searchTags) {
        List<HashtagContent> ret = new ArrayList<>();

        // Search this list's tags
        if (this instanceof HashtagContent && HashtagUtils
                .search((HashtagContent) this, searchTags))
            ret.add((HashtagContent) this);

        // Search children
        if (isGetChildrenSupported() && isChildSupported()) {
            List<HierarchyListItem> lists = getChildren();
            for (HierarchyListItem list : lists)
                ret.addAll(HashtagUtils.search(list, searchTags));
        }

        return ret;
    }

    // STATIC METHODS

    /**
     * Call dispose on list of items
     * @param items List of items
     */
    protected static void disposeItems(List<HierarchyListItem> items) {
        for (HierarchyListItem item : items) {
            if (item instanceof HierarchyListItem2)
                ((HierarchyListItem2) item).dispose();
        }
    }

    // Helper function
    protected static void refresh(HierarchyListItem item,
            HierarchyListFilter filter) {
        if (item != null && filter != null) {
            if (item instanceof HierarchyListItem2)
                ((HierarchyListItem2) item).refresh(filter);
            else
                item.refresh(filter.sort);
        }
    }

    protected static HierarchyListFilter getFilter(
            HierarchyListFilter filter, Class<?> clazz) {
        if (filter == null)
            return null;
        if (clazz.isInstance(filter))
            return filter;
        if (filter instanceof MultiFilter)
            return ((MultiFilter) filter).find(clazz);
        return null;
    }

    // Specific to feature data sets
    protected static FeatureDataStore.FeatureQueryParameters buildQueryParams(
            HierarchyListFilter filter) {
        FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
        filter = getFilter(filter, FOVFilter.class);
        if (filter != null) {
            FOVFilter.MapState fov = ((FOVFilter) filter).getMapState();
            params.visibleOnly = false;
            params.spatialFilter = new FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter(
                    new GeoPoint(fov.northBound, fov.westBound),
                    new GeoPoint(fov.southBound, fov.eastBound));
            params.ignoredFields = FeatureDataStore.FeatureQueryParameters.FIELD_ATTRIBUTES;

            // Resolution filtering in order to match what's drawn on the map
            /*double simplifyFactor = Math.hypot(
                    fov.upperLeft.getLongitude() - fov.lowerRight.getLongitude(),
                    fov.upperLeft.getLatitude() - fov.lowerRight.getLatitude()) /
                    Math.hypot(fov.left - fov.right, fov.top - fov.bottom) * 2;
            params.maxResolution = fov.drawMapResolution;
            params.ops = Collections.<FeatureDataStore.FeatureQueryParameters.SpatialOp>singleton(
                    new FeatureDataStore.FeatureQueryParameters.Simplify(simplifyFactor));*/
        }

        return params;
    }
}
