
package com.atakmap.android.wfs;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.BaseAdapter;

import com.atakmap.android.features.FeatureDataStoreDeepMapItemQuery;
import com.atakmap.android.features.FeatureDataStoreHierarchyListItem;
import com.atakmap.android.features.FeatureDataStoreMapOverlay;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem.Sort;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.overlay.MapOverlay2;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.FeatureLayer3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import gov.tak.api.annotation.DeprecatedApi;

public final class WFSManager implements MapOverlay2, DeepMapItemQuery {

    final static class Record {
        String mime;
        String uri;
        FeatureDataStore2 dataStore;
        /** may be `null` */
        FeatureLayer layer;
        FeatureLayer3 layer3;
        DeepMapItemQuery query;
        MapOverlay overlay;
    }

    private final static String TAG = "WFSManager";

    private final MapView mapView;
    private final Context context;

    private final Map<String, Record> records;
    private final Map<FeatureLayer3, Record> layerToRecord;

    private ListImpl listModel;

    public WFSManager(MapView mapView) {
        this.mapView = mapView;
        this.context = mapView.getContext();

        this.records = new HashMap<>();
        this.layerToRecord = new IdentityHashMap<>();
    }

    public synchronized int size() {
        return this.records.size();
    }

    public synchronized boolean contains(String uri) {
        return this.records.containsKey(uri);
    }

    /** @deprecated  use {@link #add(String, String, FeatureLayer3)} */
    @Deprecated
    @DeprecatedApi(since = "4.6", forRemoval = true, removeAt = "4.9")
    public synchronized boolean add(String uri, String mime,
            FeatureLayer layer) {
        if (!add(uri, mime, adapt(layer)))
            return false;
        records.get(uri).layer = layer;
        return true;
    }

    public synchronized boolean add(String uri, String mime,
            FeatureLayer3 layer) {
        if (this.records.containsKey(uri)) {
            Log.w(TAG, "Layer for " + uri + " has already been added.");
            return false;
        } else {
            boolean containsDataStore = false;
            for (Record record : records.values()) {
                containsDataStore |= (record.layer3 == layer);
                if (containsDataStore)
                    break;
            }
            if (containsDataStore) {
                Log.w(TAG,
                        "Layer has already been added for "
                                + uri);
                return false;
            }
        }

        Record record = new Record();
        record.layer = null;
        record.layer3 = layer;
        record.dataStore = layer.getDataStore();
        record.uri = uri;
        record.mime = mime;

        record.layer3.setVisible(true);
        record.overlay = new FeatureDataStoreMapOverlay(this.context,
                layer.getDataStore(),
                null,
                layer.getName(),
                null,
                new FeatureDataStoreDeepMapItemQuery(layer),
                null,
                null);

        record.query = record.overlay.getQueryFunction();
        records.put(uri, record);
        layerToRecord.put(record.layer3, record);

        this.mapView.addLayer(MapView.RenderStack.VECTOR_OVERLAYS, layer);

        return true;
    }

    /** @deprecated  */
    @Deprecated
    @DeprecatedApi(since = "4.6", forRemoval = true, removeAt = "4.9")
    public synchronized void remove(FeatureLayer layer) {
        for (Record record : records.values()) {
            if (record.layer == layer) {
                removeImpl(record.layer3);
                break;
            }
        }
    }

    public synchronized void remove(FeatureLayer3 layer) {
        if (!this.layerToRecord.containsKey(layer))
            return;

        this.removeImpl(layer);
    }

    /** @deprecated use {@link #remove2(String)} */
    @Deprecated
    @DeprecatedApi(since = "4.6", forRemoval = true, removeAt = "4.9")
    public synchronized FeatureLayer remove(String uri) {
        final Record retval = this.records.get(uri);
        if (retval == null)
            return null;
        this.removeImpl(retval.layer3);
        return retval.layer;
    }

    public synchronized FeatureLayer3 remove2(String uri) {
        final Record retval = this.records.get(uri);
        if (retval == null)
            return null;
        this.removeImpl(retval.layer3);
        return retval.layer3;
    }

    private void removeImpl(FeatureLayer3 layer) {
        this.mapView.removeLayer(MapView.RenderStack.VECTOR_OVERLAYS, layer);

        final Record record = layerToRecord.remove(layer);
        if (record != null)
            records.remove(record.uri);
    }

    private synchronized Map<String, Record> getUriMap() {
        return new HashMap<>(this.records);
    }

    /**************************************************************************/
    // MapOverlay

    @Override
    public String getIdentifier() {
        return "WFS";
    }

    @Override
    public String getName() {
        return "WFS";
    }

    @Override
    public MapGroup getRootGroup() {
        return new DefaultMapGroup("WFS");
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return this;
    }

    @Override
    public synchronized HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, Sort preferredSort) {
        return getListModel(adapter, capabilities, new HierarchyListFilter(
                preferredSort));
    }

    @Override
    public synchronized HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, HierarchyListFilter prefFilter) {
        if (this.listModel == null)
            this.listModel = new ListImpl(adapter, capabilities, prefFilter);
        else
            this.listModel.refresh(adapter, prefFilter);
        return this.listModel;
    }

    /**************************************************************************/
    // DeepMapItemQuery

    @Override
    public synchronized MapItem deepFindItem(Map<String, String> metadata) {
        MapItem retval = null;
        for (Record record : records.values()) {
            if (record.query == null)
                continue;
            retval = record.query.deepFindItem(metadata);
            if (retval != null)
                break;
        }
        return retval;
    }

    @Override
    public List<MapItem> deepFindItems(Map<String, String> metadata) {
        List<MapItem> retval = new LinkedList<>();
        for (Record record : records.values()) {
            if (record.query == null)
                continue;
            retval.addAll(record.query.deepFindItems(metadata));
        }
        return retval;
    }

    @Override
    public MapItem deepFindClosestItem(GeoPoint location, double threshold,
            Map<String, String> metadata) {

        MapItem retval = null;
        for (Record record : records.values()) {
            if (record.query == null)
                continue;
            retval = record.query.deepFindClosestItem(location, threshold,
                    metadata);
            if (retval != null)
                break;
        }
        return retval;
    }

    @Override
    public Collection<MapItem> deepFindItems(GeoPoint location, double radius,
            Map<String, String> metadata) {
        List<MapItem> retval = new LinkedList<>();
        for (Record record : records.values()) {
            if (record.query == null)
                continue;
            retval.addAll(
                    record.query.deepFindItems(location, radius, metadata));
        }
        return retval;
    }

    @Override
    public Collection<MapItem> deepFindItems(GeoBounds bounds,
            Map<String, String> metadata) {
        List<MapItem> retval = new LinkedList<>();
        for (Record record : records.values()) {
            if (record.query == null)
                continue;
            retval.addAll(record.query.deepFindItems(bounds, metadata));
        }
        return retval;
    }

    @Override
    public MapItem deepHitTest(int xpos, int ypos, GeoPoint point,
            MapView view) {
        MapItem retval = null;
        for (Record record : records.values()) {
            if (record.query == null)
                continue;
            retval = record.query.deepHitTest(xpos, ypos, point, view);
            if (retval != null)
                break;
        }
        return retval;
    }

    @Override
    public SortedSet<MapItem> deepHitTestItems(int xpos, int ypos,
            GeoPoint point, MapView view) {
        SortedSet<MapItem> retval = new TreeSet<>(
                MapItem.ZORDER_HITTEST_COMPARATOR);
        for (Record record : records.values()) {
            if (record.query == null)
                continue;
            final SortedSet<MapItem> results = record.query.deepHitTestItems(
                    xpos,
                    ypos, point, view);
            if (results != null)
                retval.addAll(results);
        }
        return retval;
    }

    /**************************************************************************/

    private class ListImpl extends AbstractHierarchyListItem2 implements
            Visibility2, Search, Delete {

        private final Map<String, SourceListItem> itemMap = new HashMap<>();

        public ListImpl(BaseAdapter adapter, long capabilities,
                HierarchyListFilter prefFilter) {
            this.asyncRefresh = true;
            refresh(adapter, prefFilter);
        }

        @Override
        public String getUID() {
            return getIdentifier();
        }

        @Override
        public String getTitle() {
            return WFSManager.this.getName();
        }

        @Override
        public int getPreferredListIndex() {
            return -1;
        }

        @Override
        public int getDescendantCount() {
            int retval = 0;
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem child : children)
                retval += child.getDescendantCount();
            return retval;
        }

        @Override
        public boolean isChildSupported() {
            return true;
        }

        @Override
        public String getIconUri() {
            return null;
        }

        @Override
        public int getIconColor() {
            return -1;
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
            List<HierarchyListItem> filtered = new ArrayList<>();
            Map<String, Record> uriMap = getUriMap();
            Map<String, SourceListItem> itemMap;
            List<String> removed;
            synchronized (this.itemMap) {
                itemMap = new HashMap<>(this.itemMap);
                removed = new ArrayList<>(this.itemMap.keySet());
            }

            // Create/refresh items
            for (Map.Entry<String, Record> entry : uriMap.entrySet()) {
                SourceListItem src = itemMap.get(entry.getKey());
                if (src == null) {
                    src = new SourceListItem(entry.getValue().layer3,
                            entry.getKey(),
                            WFSManager.this.context, this.listener,
                            this.filter);
                    itemMap.put(entry.getKey(), src);
                } else
                    src.syncRefresh(this.listener, this.filter);
                filtered.add(src);
                removed.remove(entry.getKey());
            }

            // Remove remaining data sets from item map
            for (String key : removed) {
                SourceListItem src = itemMap.remove(key);
                if (src != null)
                    src.dispose();
            }
            synchronized (this.itemMap) {
                this.itemMap.clear();
                this.itemMap.putAll(itemMap);
            }

            // Sort
            sortItems(filtered);

            // Update
            updateChildren(filtered);
        }

        @Override
        protected void disposeChildren() {
            synchronized (this.children) {
                this.children.clear();
            }
        }

        @Override
        public void dispose() {
            disposeChildren();
        }

        @Override
        public boolean setVisible(boolean visible) {
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem child : children) {
                Visibility vis = child.getAction(Visibility.class);
                if (vis != null)
                    vis.setVisible(visible);
            }
            return true;
        }

        @Override
        public Set<HierarchyListItem> find(String terms) {
            Set<HierarchyListItem> results = new HashSet<>();
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem child : children) {
                Search search = child.getAction(Search.class);
                if (search != null)
                    results.addAll(search.find(terms));
            }
            return results;
        }

        @Override
        public boolean delete() {
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem child : children) {
                Delete del = child.getAction(Delete.class);
                if (del != null)
                    del.delete();
            }
            return true;
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }
    }

    private static class SourceListItem extends
            FeatureDataStoreHierarchyListItem {

        private final String uri;

        public SourceListItem(FeatureLayer3 layer, String uri, Context context,
                BaseAdapter listener, HierarchyListFilter filter) {

            super(layer.getDataStore(), layer.getName(), null, null, null,
                    context, listener, filter);

            this.uri = uri;

            this.supportedActions.add(Delete.class);
        }

        @Override
        public boolean delete() {
            Intent intent = new Intent(
                    ImportExportMapComponent.ACTION_DELETE_DATA);
            intent.putExtra(ImportReceiver.EXTRA_CONTENT, WFSImporter.CONTENT);
            intent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                    WFSImporter.MIME_URL);
            intent.putExtra(ImportReceiver.EXTRA_URI, this.uri);
            AtakBroadcast.getInstance().sendBroadcast(intent);
            return true;
        }

        @Override
        public boolean isMultiSelectSupported() {
            // Prevents user from descending into set
            return false;
        }
    }

    static FeatureLayer3 adapt(FeatureLayer layer) {
        final FeatureLayer3 adapted = new FeatureLayer3(layer.getName(),
                Adapters.adapt(layer.getDataStore()));
        layer.addOnLayerVisibleChangedListener(
                new Layer.OnLayerVisibleChangedListener() {
                    @Override
                    public void onLayerVisibleChanged(Layer layer) {
                        adapted.setVisible(layer.isVisible());
                    }
                });
        return adapted;
    }
}
