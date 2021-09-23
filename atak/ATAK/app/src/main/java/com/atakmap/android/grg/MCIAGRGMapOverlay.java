
package com.atakmap.android.grg;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.widget.BaseAdapter;

import com.atakmap.android.features.FeatureHierarchyListItem;
import com.atakmap.android.features.FeatureSetHierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem2;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapCoreIntentsComponent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureSetCursor;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.PersistentDataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.PersistentDataSourceFeatureDataStore2;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetDescriptorCursor;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetQueryParameters;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MCIAGRGMapOverlay extends AbstractMapOverlay2 {

    private final Context context;
    private final LocalRasterDataStore grgDatabase;

    public MCIAGRGMapOverlay(final Context context,
            final LocalRasterDataStore grgDatabase) {
        this.context = context;
        this.grgDatabase = grgDatabase;
    }

    @Override
    public String getIdentifier() {
        return MCIAGRGMapOverlay.class.getName();
    }

    @Override
    public String getName() {
        return "MCIA GRGs";
    }

    @Override
    public MapGroup getRootGroup() {
        return null;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    @Override
    public HierarchyListItem getListModel(
            BaseAdapter adapter, long capabilityies,
            HierarchyListFilter preferredFilter) {
        return new ListModel(adapter, preferredFilter);
    }

    private class ListModel extends AbstractHierarchyListItem2 implements
            Search {

        private final ListMap<DatasetDescriptor, GRGListModel> _lists = new ListMap<DatasetDescriptor, GRGListModel>() {
            @Override
            public String getUID(DatasetDescriptor desc) {
                return desc.getUri();
            }

            @Override
            public GRGListModel createList(DatasetDescriptor desc) {
                return new GRGListModel(context, listener, filter, desc);
            }
        };

        private int _descCount;

        ListModel(BaseAdapter listener, HierarchyListFilter filter) {
            this.asyncRefresh = true;
            this.reusable = true;
            refresh(listener, filter);
        }

        @Override
        public String getTitle() {
            return getName();
        }

        @Override
        public int getDescendantCount() {
            return _descCount;
        }

        @Override
        public Drawable getIconDrawable() {
            return context.getDrawable(R.drawable.ic_overlay_gridlines);
        }

        @Override
        public Object getUserObject() {
            return MCIAGRGMapOverlay.this;
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        @Override
        protected void refreshImpl() {

            // Query MCIA GRGs from GRG database
            DatasetQueryParameters params = new DatasetQueryParameters();
            params.providers = Collections.singleton("mcia-grg");
            params.order = Collections
                    .singleton(DatasetQueryParameters.Name.INSTANCE);
            List<DatasetDescriptor> results = new ArrayList<>();
            DatasetDescriptorCursor result = null;
            try {
                result = grgDatabase.queryDatasets(params);
                while (result.moveToNext())
                    results.add(result.get());
            } finally {
                if (result != null)
                    result.close();
            }

            // Get corresponding lists and update map if needed
            List<GRGListModel> lists = _lists.update(results);

            // Filter
            int descCount = 0;
            List<HierarchyListItem> items = new ArrayList<>(lists.size());
            for (GRGListModel list : lists) {
                if (this.filter.accept(list)) {
                    descCount += list.numFeatures;
                    list.syncRefresh(this.listener, this.filter);
                    items.add(list);
                }
            }

            // Sort
            sortItems(items);

            // Update
            _descCount = descCount;
            updateChildren(items);
        }

        /**********************************************************************/
        // Search

        @Override
        public Set<HierarchyListItem> find(String terms) {
            Set<HierarchyListItem> retval = new LinkedHashSet<>();
            List<Search> items = getChildActions(Search.class);
            for (Search s : items)
                retval.addAll(s.find(terms));
            return retval;
        }
    }

    private static class GRGListModel extends AbstractHierarchyListItem2
            implements GoTo, Search {
        private final static Set<Class<? extends Action>> ACTION_FILTER = new HashSet<>();
        static {
            ACTION_FILTER.add(GoTo.class);
            ACTION_FILTER.add(Search.class);
        }

        private final Context context;
        private final DatasetDescriptor layerInfo;
        private final BaseAdapter listener;
        private final HierarchyListFilter filter;

        private final Map<String, String> groupAliases;
        private final String[] groupOrder;
        private final int numFeatures;

        private final FeatureDataStore spatialdb;

        private final ListMap<FeatureSet, FeatureSetHierarchyListItem> _lists = new ListMap<FeatureSet, FeatureSetHierarchyListItem>() {
            @Override
            public String getUID(FeatureSet fs) {
                return String.valueOf(fs.getId());
            }

            @Override
            public FeatureSetHierarchyListItem createList(FeatureSet fs) {
                if (spatialdb == null)
                    return null;
                String name = fs.getName();
                String alias = groupAliases.get(name);
                if (alias == null)
                    alias = name;
                return new FeatureSetHierarchyListItem(context, spatialdb,
                        fs, alias, filter, listener, null, null, null);
            }
        };

        GRGListModel(Context context, BaseAdapter listener,
                HierarchyListFilter filter,
                DatasetDescriptor layerInfo) {
            this.context = context;
            this.layerInfo = layerInfo;
            this.listener = listener;
            this.filter = filter;
            this.reusable = true;

            this.groupAliases = new HashMap<>();
            this.groupOrder = new String[3];

            int order = 0;

            String group;

            group = layerInfo.getExtraData("buildingsGroup");
            if (group != null) {
                this.groupAliases.put(group, "Buildings");
                this.groupOrder[order++] = group;
            }

            group = layerInfo.getExtraData("sectionsGroup");
            if (group != null) {
                this.groupAliases.put(group, "Sections");
                this.groupOrder[order++] = group;
            }

            group = layerInfo.getExtraData("subsectionsGroup");
            if (group != null) {
                this.groupAliases.put(group, "Subsections");
                this.groupOrder[order++] = group;
            }

            this.numFeatures = MathUtils.parseInt(
                    layerInfo.getExtraData("numFeatures"), 0);

            // XXX - jsqlite.Database handles finalization itself which is
            //       different from the explicit close required for android
            //       SQLiteDatabase. ideally, there would be some kind of
            //       dispose method for the list item where we would explicitly
            //       close the spatialdb
            File dbFile = new File(this.layerInfo.getExtraData("spatialdb"));
            FeatureDataStore db;
            try {
                db = new PersistentDataSourceFeatureDataStore2(dbFile);
            } catch (Exception e) {
                db = new PersistentDataSourceFeatureDataStore(dbFile);
            }
            this.spatialdb = db;
        }

        @Override
        public String getTitle() {
            return this.layerInfo.getName();
        }

        @Override
        public int getDescendantCount() {
            return this.numFeatures;
        }

        @Override
        public Object getUserObject() {
            return layerInfo;
        }

        @Override
        protected void refreshImpl() {
            List<FeatureSet> sets = new ArrayList<>();

            if (spatialdb != null) {
                FeatureSetCursor c = null;
                try {
                    FeatureSetQueryParameters params = new FeatureSetQueryParameters();
                    c = this.spatialdb.queryFeatureSets(params);
                    while (c.moveToNext())
                        sets.add(c.get());
                } finally {
                    if (c != null)
                        c.close();
                }
            }

            List<FeatureSetHierarchyListItem> lists = _lists.update(sets);
            List<HierarchyListItem> items = new ArrayList<>(lists.size());
            for (FeatureSetHierarchyListItem list : lists) {
                if (this.filter.accept(list))
                    items.add(list);
            }

            sortItems(items);
            updateChildren(items);
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        /**********************************************************************/
        // Go To

        @Override
        public boolean goTo(boolean select) {
            final Intent intent = new Intent(
                    MapCoreIntentsComponent.ACTION_PAN_ZOOM);
            Envelope mbb = this.layerInfo.getMinimumBoundingBox();
            intent.putExtra(
                    "shape",
                    new String[] {
                            new GeoPoint(mbb.maxY, mbb.minX)
                                    .toStringRepresentation(),
                            new GeoPoint(mbb.maxY, mbb.maxX)
                                    .toStringRepresentation(),
                            new GeoPoint(mbb.minY, mbb.maxX)
                                    .toStringRepresentation(),
                            new GeoPoint(mbb.minY, mbb.minX)
                                    .toStringRepresentation(),
                    });
            AtakBroadcast.getInstance().sendBroadcast(intent);

            return false;
        }

        /**********************************************************************/
        // Search

        @Override
        public Set<HierarchyListItem> find(String terms) {
            Set<HierarchyListItem> retval = new HashSet<>();
            if (spatialdb == null)
                return null;

            FeatureQueryParameters params = new FeatureQueryParameters();
            params.featureNames = Collections.singleton(terms + "%");

            params.order = new LinkedList<>();
            params.order
                    .add(FeatureQueryParameters.FeatureSet.INSTANCE);
            params.order
                    .add(FeatureQueryParameters.FeatureName.INSTANCE);

            FeatureCursor result = null;
            try {
                result = this.spatialdb.queryFeatures(params);
                while (result.moveToNext()) {
                    retval.add(new FeatureHierarchyListItem(
                            this.spatialdb,
                            result.get()));
                }
            } finally {
                if (result != null)
                    result.close();
            }
            return retval;
        }
    }

    /**
     * Convenience class for reusable hierarchy lists mapped by some UID
     * @param <S> Class for the source item to create lists from
     * @param <L> Class for the list items
     *
     * TODO: Move this into its own separate class and utilize elsewhere
     *       so we can cut down on duplicate code
     */
    private abstract static class ListMap<S, L extends HierarchyListItem>
            extends HashMap<String, L> {

        /**
         * Get the UID of an entry in this list map
         * @param source Source object
         * @return Entry UID
         */
        public abstract String getUID(S source);

        /**
         * Create a new list given a source object
         * @param source Source object
         * @return Newly created list
         */
        public abstract L createList(S source);

        /**
         * Given a list of entries get the corresponding lists while creating or
         * removing newly added/removed lists
         * @param sources List of sources to update lists from
         * @return List items
         */
        public List<L> update(Collection<S> sources) {
            List<L> lists = new ArrayList<>(sources.size());
            synchronized (this) {
                // Get/add new lists
                Map<String, L> removed = new HashMap<>(this);
                for (S entry : sources) {
                    String uid = getUID(entry);
                    L list = get(uid);
                    if (list == null) {
                        list = createList(entry);
                        if (list != null)
                            put(uid, list);
                    }
                    lists.add(list);
                    removed.remove(uid);
                }

                // Remove and dispose lists that are no longer in the provided UIDs
                for (String uid : removed.keySet()) {
                    L list = remove(uid);
                    if (list instanceof HierarchyListItem2)
                        ((HierarchyListItem2) list).dispose();
                }
            }
            return lists;
        }
    }
}
