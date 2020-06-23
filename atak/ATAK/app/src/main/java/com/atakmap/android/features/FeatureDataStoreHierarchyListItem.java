
package com.atakmap.android.features;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.BaseAdapter;

import com.atakmap.android.features.FeatureDataStorePathUtils.PathEntry;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureSetCursor;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureSetQueryParameters;
import com.atakmap.math.MathUtils;

public class FeatureDataStoreHierarchyListItem extends
        AbstractHierarchyListItem2 implements Visibility, Search, Delete {

    private static final String TAG = "FeatureDataStoreHierarchyListItem";

    protected final Context context;
    protected final FeatureDataStore spatialDb;
    private boolean rootsValid;
    private boolean validating = false;
    protected final String typeFilter;
    protected final String mimeType;
    protected final String iconUri;
    protected final String title;
    protected final String uid;
    protected final Set<Class<? extends Action>> supportedActions;
    private int descendantCount;
    private int vizCount;
    private boolean pendingRefresh;

    // Cache the roots path entries so we don't have to rescan it every refresh
    // Cache should only be cleared when a data store item is added or removed
    private static final Map<String, Cache> rootsCache = new HashMap<>();

    public FeatureDataStoreHierarchyListItem(FeatureDataStore spatialDb,
            String title, String iconUri, Context context,
            BaseAdapter listener, HierarchyListFilter filter) {
        this(spatialDb, title, iconUri, null, null, context, listener, filter);
    }

    public FeatureDataStoreHierarchyListItem(FeatureLayer layer,
            String iconUri, Context context, BaseAdapter listener,
            HierarchyListFilter filter) {
        this(layer.getDataStore(),
                layer.getName(),
                iconUri,
                null,
                null,
                context,
                listener,
                filter);
    }

    public FeatureDataStoreHierarchyListItem(FeatureDataStore spatialDb,
            String title,
            String iconUri,
            String typeFilter,
            String mimeType,
            Context context,
            BaseAdapter listener,
            HierarchyListFilter filter) {
        this.spatialDb = spatialDb;
        this.title = title;
        this.iconUri = iconUri;
        this.typeFilter = typeFilter;
        this.mimeType = mimeType;
        this.context = context;
        this.listener = listener;
        this.rootsValid = false;
        this.uid = this.spatialDb.getUri() + "::" + this.typeFilter;
        this.pendingRefresh = false;

        this.supportedActions = new HashSet<>();
        this.supportedActions.add(Search.class);
        if (MathUtils.hasBits(this.spatialDb.getModificationFlags(),
                FeatureDataStore.MODIFY_FEATURESET_DELETE) &&
                (this.spatialDb instanceof DataSourceFeatureDataStore))
            this.supportedActions.add(Delete.class);
        if (MathUtils.hasBits(this.spatialDb.getVisibilitySettingsFlags(),
                FeatureDataStore.VISIBILITY_SETTINGS_FEATURESET))
            this.supportedActions.add(Visibility.class);

        this.descendantCount = -1;
        this.vizCount = -1;
        if (this.listener != null)
            this.spatialDb
                    .addOnDataStoreContentChangedListener(
                            new ContentChangedHandler(
                                    this));

        this.asyncRefresh = true;

        refresh(filter);
    }

    /**
     * Store roots from cache and do filtering on children feature sets
     * @param force Force refresh
     */
    private void validateRoots(boolean force) {
        if (!this.rootsValid || force) {

            // Let refresh do this on background thread
            final Cache cache = rootsCache.get(this.uid);
            if (cache == null)
                return;

            if (this.validating) {
                this.pendingRefresh = true;
                return;
            }

            this.validating = true;
            List<PathEntry> roots = new LinkedList<>(cache.roots);

            List<HierarchyListItem> filtered = new ArrayList<>();

            // Filter
            for (PathEntry path : roots) {
                // make sure the item has content for the current filtering
                if (isEmpty(this.spatialDb, path, this.filter))
                    continue;
                FeatureSetHierarchyListItem item = new FeatureSetHierarchyListItem(
                        this.context, this.spatialDb, path,
                        this.filter, this.listener, this.typeFilter,
                        this.mimeType, this.iconUri);
                if (this.filter.accept(item))
                    filtered.add(item);
            }

            // Update children
            updateChildren(filtered);

            this.rootsValid = true;
            this.validating = false;

            // In case another refresh was requested while this method was busy
            // Fixes issue where a new data set received from another device
            // doesn't appear in the current list
            if (this.pendingRefresh) {
                this.pendingRefresh = false;
                validateRoots(true);
            }
        }
    }

    /**
     * Rescan and cache roots for data store type
     * Expensive operation - should be called on background thread
     */
    private boolean rescanRoots() {
        if (rootsCache.containsKey(this.uid))
            return false;

        if (Thread.currentThread() == Looper.getMainLooper().getThread())
            Log.w(TAG,
                    "Calling expensive operation validateRoots on UI thread",
                    new Throwable());

        //beginTimeMeasure(getTitle() + " rescanRoots()");
        List<PathEntry> roots = new ArrayList<>();
        FeatureDataStore.FeatureSetCursor result = null;
        try {
            FeatureDataStore.FeatureSetQueryParameters params = new FeatureDataStore.FeatureSetQueryParameters();
            if (this.typeFilter != null)
                params.types = Collections.singleton(this.typeFilter);

            result = this.spatialDb.queryFeatureSets(params);

            Map<File, PathEntry> fileToRoot = new HashMap<>();

            FeatureSet featureSet;
            File file;
            PathEntry root;
            while (result.moveToNext()) {
                featureSet = result.get();
                final String path = featureSet.getName();
                // XXX - prefer not to do instance check intra-loop
                if (spatialDb instanceof DataSourceFeatureDataStore)
                    file = ((DataSourceFeatureDataStore) spatialDb)
                            .getFile(featureSet);
                else
                    file = null;
                root = fileToRoot.get(file);
                if (root == null)
                    fileToRoot.put(file, root = new PathEntry(""));
                FeatureDataStorePathUtils.processPath(
                        root,
                        featureSet.getId(),
                        path,
                        FeatureDataStorePathUtils.getPathDepth(path),
                        0);
            }

            for (PathEntry r : fileToRoot.values())
                roots.addAll(r.children.values());

            Collections.sort(roots, new Comparator<PathEntry>() {
                @Override
                public int compare(PathEntry lhs, PathEntry rhs) {
                    return lhs.folder.compareToIgnoreCase(rhs.folder);
                }
            });
        } finally {
            if (result != null)
                result.close();
        }
        //endTimeMeasure(getTitle() + " rescanRoots()");
        rootsCache.put(this.uid, new Cache(roots));
        this.rootsValid = false;
        this.descendantCount = queryDescendants();
        return true;
    }

    @Override
    public String getIconUri() {
        return this.iconUri;
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public String getUID() {
        return this.uid;
    }

    @Override
    public int getDescendantCount() {
        if (this.descendantCount == -1)
            // Wait for refresh to finish
            return 0;
        return this.descendantCount;
    }

    private int queryDescendants() {
        //beginTimeMeasure(getTitle() + " getDescendantCount()");
        try {
            FeatureQueryParameters params = buildQueryParams();
            if (this.typeFilter != null)
                params.types = Collections.singleton(this.typeFilter);
            return this.spatialDb.queryFeaturesCount(params);
        } finally {
            //endTimeMeasure(getTitle() + " getDescendantCount()");
        }
    }

    private int queryVisible() {
        //beginTimeMeasure(getTitle() + " isVisible()");
        try {
            FeatureDataStore.FeatureSetQueryParameters params = new FeatureDataStore.FeatureSetQueryParameters();
            if (this.typeFilter != null)
                params.types = Collections.singleton(this.typeFilter);
            params.visibleOnly = true;
            params.limit = 1;
            return this.spatialDb.queryFeatureSetsCount(params);
        } finally {
            //endTimeMeasure(getTitle() + " isVisible()");
        }
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        //validateRoots(false);
        return super.getChildAt(index);
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (this.supportedActions.contains(clazz))
            return clazz.cast(this);
        else
            return null;
    }

    @Override
    public Object getUserObject() {
        return null;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    protected void refreshImpl() {
        if (!rescanRoots())
            this.descendantCount = queryDescendants();
        this.vizCount = queryVisible();
        validateRoots(true);
    }

    @Override
    public boolean hideIfEmpty() {
        return true;
    }

    /**********************************************************************/
    // Visible

    @Override
    public boolean setVisible(boolean visible) {
        FeatureDataStore.FeatureSetQueryParameters params = new FeatureDataStore.FeatureSetQueryParameters();
        if (this.typeFilter != null)
            params.types = Collections.singleton(this.typeFilter);

        this.spatialDb.setFeatureSetsVisible(params, visible);
        this.vizCount = visible ? 1 : 0;
        refresh(this.filter);
        return true;
    }

    @Override
    public boolean isVisible() {
        if (this.vizCount == -1)
            // Wait for refresh to finish
            return true;
        return this.vizCount > 0;
    }

    /**********************************************************************/
    // Search

    @Override
    public Set<HierarchyListItem> find(String terms) {
        FeatureQueryParameters params = buildQueryParams();
        if (terms.length() >= 2)
            terms = "%" + terms + "%";
        else
            terms = terms + "%";
        params.featureNames = Collections.singleton(terms);

        if (this.typeFilter != null)
            params.types = Collections.singleton(this.typeFilter);

        params.ignoredFields = FeatureQueryParameters.FIELD_ATTRIBUTES;

        params.order = new LinkedList<>();
        params.order
                .add(FeatureDataStore.FeatureQueryParameters.FeatureSet.INSTANCE);
        params.order
                .add(FeatureDataStore.FeatureQueryParameters.FeatureName.INSTANCE);

        params.limit = 250;

        Set<HierarchyListItem> retval = new HashSet<>();
        FeatureCursor result = null;
        try {
            result = this.spatialDb.queryFeatures(params);
            while (result.moveToNext()) {
                retval.add(new FeatureHierarchyListItem(
                        this.spatialDb,
                        result.get()));
            }
        } finally {
            if (result != null)
                result.close();
        }
        return retval;
    }

    /**************************************************************************/
    // Delete

    @Override
    public boolean delete() {
        if (this.typeFilter != null) {
            this.spatialDb.deleteAllFeatureSets();
        } else {
            Set<Long> fsids = new HashSet<>();

            FeatureSetCursor result = null;
            try {
                FeatureSetQueryParameters params = new FeatureSetQueryParameters();
                params.types = Collections.singleton(this.typeFilter);

                result = this.spatialDb.queryFeatureSets(params);
                while (result.moveToNext())
                    fsids.add(result.get().getId());
            } finally {
                if (result != null)
                    result.close();
            }

            for (Long fsid : fsids)
                this.spatialDb.deleteFeatureSet(fsid);
        }
        return true;
    }

    public FeatureQueryParameters buildQueryParams() {
        return buildQueryParams(this.filter);
    }

    /**************************************************************************/

    static boolean isEmpty(FeatureDataStore spatialDb,
            FeatureDataStorePathUtils.PathEntry entry,
            HierarchyListFilter filter) {
        FeatureDataStore.FeatureQueryParameters params = buildQueryParams(
                filter);
        if (entry.fsid != FeatureDataStore.FEATURESET_ID_NONE
                && entry.childFsids.isEmpty()) {
            params.featureSetIds = Collections.singleton(entry.fsid);
        } else {
            params.featureSets = new ArrayList<>(2);
            params.featureSets.add(entry.folder);
            params.featureSets.add(entry.folder + "/%");
            params.featureSetIds = entry.childFsids;
            if (entry.fsid != FeatureDataStore.FEATURESET_ID_NONE) {
                params.featureSetIds = new HashSet<>(params.featureSetIds);
                params.featureSetIds.add(entry.fsid);
            }
        }
        params.limit = 1;
        return spatialDb.queryFeaturesCount(params) <= 0;
    }

    /**************************************************************************/

    private static class ContentChangedHandler implements
            FeatureDataStore.OnDataStoreContentChangedListener {
        private final WeakReference<FeatureDataStoreHierarchyListItem> ref;
        private final String refUID;

        public ContentChangedHandler(FeatureDataStoreHierarchyListItem item) {
            this.ref = new WeakReference<>(
                    item);
            this.refUID = item.getUID();
        }

        @Override
        public void onDataStoreContentChanged(
                final FeatureDataStore dataStore) {
            final FeatureDataStoreHierarchyListItem item = this.ref.get();

            rootsCache.remove(this.refUID);

            if (item == null) {

                Thread t = new Thread(TAG + "-Changed") {
                    @Override
                    public void run() {
                        dataStore
                                .removeOnDataStoreContentChangedListener(
                                        ContentChangedHandler.this);
                    }
                };
                t.setPriority(Thread.MIN_PRIORITY);
                t.start();
                return;
            }

            //Log.w(TAG, refUID + " onDataStoreContentChanged");

            synchronized (this) {
                item.refresh(item.filter);
            }
        }
    }

    private static class Cache {
        List<PathEntry> roots;

        public Cache(List<PathEntry> roots) {
            this.roots = new ArrayList<>();
            this.roots.addAll(roots);
        }
    }

    private final Map<String, Long> startTimes = new HashMap<>();

    private void beginTimeMeasure(String tag) {
        this.startTimes.put(tag, android.os.SystemClock.elapsedRealtime());
    }

    private void endTimeMeasure(String tag) {
        Long startTime = this.startTimes.get(tag);
        if (startTime != null)
            Log.d(TAG, tag + " ("
                    + (android.os.SystemClock.elapsedRealtime() - startTime)
                    + "ms)" + " on " + Thread.currentThread().getName());
    }
}
