
package com.atakmap.android.grg;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.BaseAdapter;

import com.atakmap.android.features.FeatureHierarchyListItem;
import com.atakmap.android.features.FeatureSetHierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.Location;
import com.atakmap.android.maps.MapCoreIntentsComponent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.PersistentDataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
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

        ListModel(BaseAdapter listener, HierarchyListFilter filter) {
            refresh(listener, filter);
        }

        @Override
        public String getTitle() {
            return MCIAGRGMapOverlay.this.getName();
        }

        @Override
        public int getChildCount() {
            RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
            params.providers = Collections.singleton("mcia-grg");

            return MCIAGRGMapOverlay.this.grgDatabase
                    .queryDatasetsCount(params);
        }

        @Override
        public int getDescendantCount() {
            RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
            params.providers = Collections.singleton("mcia-grg");

            RasterDataStore.DatasetDescriptorCursor result = null;
            try {
                result = MCIAGRGMapOverlay.this.grgDatabase
                        .queryDatasets(params);

                int retval = 0;
                while (result.moveToNext())
                    retval += Integer.parseInt(DatasetDescriptor.getExtraData(
                            result.get(), "numFeatures", "0"));
                return retval;
            } finally {
                if (result != null)
                    result.close();
            }
        }

        @Override
        public HierarchyListItem getChildAt(int index) {
            RasterDataStore.DatasetDescriptorCursor result = null;
            try {
                RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
                params.providers = Collections.singleton("mcia-grg");
                params.order = Collections.<RasterDataStore.DatasetQueryParameters.Order> singleton(
                        RasterDataStore.DatasetQueryParameters.Name.INSTANCE);

                result = MCIAGRGMapOverlay.this.grgDatabase
                        .queryDatasets(params);

                // XXX - just specify limit/offset ???
                int i = 0;
                while (result.moveToNext()) {
                    if (i == index) {
                        return new GRGListModel(MCIAGRGMapOverlay.this.context,
                                this.listener,
                                this.filter,
                                result.get());
                    }
                    i++;
                }

                throw new IndexOutOfBoundsException();
            } finally {
                if (result != null)
                    result.close();
            }
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
        public void dispose() {
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        @Override
        protected void refreshImpl() {
        }

        @Override
        public HierarchyListFilter refresh(HierarchyListFilter filter) {
            return this.filter = filter;
        }

        /**********************************************************************/
        // Search

        @Override
        public Set<HierarchyListItem> find(String terms) {
            Set<HierarchyListItem> retval = new LinkedHashSet<>();
            final int count = this.getChildCount();
            Search childSearch;
            for (int i = 0; i < count; i++) {
                childSearch = this.getChildAt(i).getAction(Search.class);
                if (childSearch != null)
                    retval.addAll(childSearch.find(terms));
            }
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

        private final FeatureDataStore spatialdb;

        GRGListModel(Context context, BaseAdapter listener,
                HierarchyListFilter filter,
                DatasetDescriptor layerInfo) {
            this.context = context;
            this.layerInfo = layerInfo;
            this.listener = listener;
            this.filter = filter;

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

            // XXX - jsqlite.Database handles finalization itself which is
            //       different from the explicit close required for android
            //       SQLiteDatabase. ideally, there would be some kind of
            //       dispose method for the list item where we would explicitly
            //       close the spatialdb
            this.spatialdb = new PersistentDataSourceFeatureDataStore(new File(
                    this.layerInfo.getExtraData("spatialdb")));
        }

        @Override
        public String getTitle() {
            return this.layerInfo.getName();
        }

        @Override
        public int getChildCount() {
            return this.spatialdb
                    .queryFeatureSetsCount(
                            new FeatureDataStore.FeatureSetQueryParameters());
        }

        @Override
        public int getDescendantCount() {
            return this.spatialdb
                    .queryFeaturesCount(
                            new FeatureDataStore.FeatureQueryParameters());
        }

        @Override
        public HierarchyListItem getChildAt(int index) {
            // XXX - wildly inefficient
            FeatureDataStore.FeatureSetCursor result = null;
            try {
                FeatureDataStore.FeatureSetQueryParameters params = new FeatureDataStore.FeatureSetQueryParameters();
                params.names = Collections.singleton(this.groupOrder[index]);

                params.limit = 1;

                result = this.spatialdb.queryFeatureSets(params);
                if (!result.moveToNext())
                    throw new IllegalStateException();

                return new ActionFilteredHierarchyListItem(
                        new FeatureSetHierarchyListItem(this.context,
                                this.spatialdb,
                                result.get(),
                                this.groupAliases.get(this.groupOrder[index]),
                                this.filter,
                                this.listener,
                                null,
                                null,
                                null),
                        ACTION_FILTER);
            } finally {
                if (result != null)
                    result.close();
            }
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
        public void dispose() {
        }

        @Override
        protected void refreshImpl() {
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
            FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
            params.featureNames = Collections.singleton(terms + "%");

            params.order = new LinkedList<>();
            params.order
                    .add(FeatureDataStore.FeatureQueryParameters.FeatureSet.INSTANCE);
            params.order
                    .add(FeatureDataStore.FeatureQueryParameters.FeatureName.INSTANCE);

            Set<HierarchyListItem> retval = new HashSet<>();
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

    private static class ActionFilteredHierarchyListItem implements
            HierarchyListItem {

        protected final HierarchyListItem item;
        private final Set<Class<? extends Action>> filter;

        ActionFilteredHierarchyListItem(HierarchyListItem item,
                Set<Class<? extends Action>> filter) {
            this.item = item;
            this.filter = filter;
        }

        @Override
        public String getUID() {
            return this.item.getUID();
        }

        @Override
        public String getTitle() {
            return this.item.getTitle();
        }

        @Override
        public int getPreferredListIndex() {
            return this.item.getPreferredListIndex();
        }

        @Override
        public int getChildCount() {
            return this.item.getChildCount();
        }

        @Override
        public int getDescendantCount() {
            return this.item.getDescendantCount();
        }

        @Override
        public HierarchyListItem getChildAt(int index) {
            final HierarchyListItem child = this.item.getChildAt(index);
            if (child instanceof Location)
                return new ActionFilteredLocationHierarchyListItem(child,
                        this.filter);
            else
                return new ActionFilteredHierarchyListItem(child, this.filter);
        }

        @Override
        public boolean isChildSupported() {
            return true;
        }

        @Override
        public String getIconUri() {
            return this.item.getIconUri();
        }

        @Override
        public int getIconColor() {
            return this.item.getIconColor();
        }

        @Override
        public Object setLocalData(String s, Object o) {
            return this.item.setLocalData(s, o);
        }

        @Override
        public Object getLocalData(String s) {
            return this.item.getLocalData(s);
        }

        @Override
        public <T> T getLocalData(String s, Class<T> clazz) {
            return this.item.getLocalData(s, clazz);
        }

        @Override
        public <T extends Action> T getAction(Class<T> clazz) {
            if (!this.filter.contains(clazz))
                return null;
            return this.item.getAction(clazz);
        }

        @Override
        public Object getUserObject() {
            return this.item.getUserObject();
        }

        @Override
        public View getExtraView() {
            return null;
        }

        @Override
        public Sort refresh(Sort sortHint) {
            return this.item.refresh(sortHint);
        }
    }

    private static final class ActionFilteredLocationHierarchyListItem extends
            ActionFilteredHierarchyListItem implements Location {

        ActionFilteredLocationHierarchyListItem(HierarchyListItem item,
                Set<Class<? extends Action>> filter) {
            super(item, filter);
        }

        @Override
        public GeoPointMetaData getLocation() {
            return ((Location) this.item).getLocation();
        }

        @Override
        public String getFriendlyName() {
            return ((Location) this.item).getFriendlyName();
        }

        @Override
        public String getUID() {
            return this.item.getUID();
        }

        @Override
        public MapOverlay getOverlay() {
            return ((Location) this.item).getOverlay();
        }
    }
}
