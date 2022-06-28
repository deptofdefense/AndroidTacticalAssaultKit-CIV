
package com.atakmap.android.wfs;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.BaseAdapter;

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
import com.atakmap.map.layer.feature.FeatureLayer;

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
import java.util.TreeMap;
import java.util.TreeSet;

public final class WFSManager implements MapOverlay2, DeepMapItemQuery {

    private final static String TAG = "WFSManager";

    private final MapView mapView;
    private final Context context;

    private final Map<String, String> uriToMime;
    private final Map<String, FeatureLayer> uriToLayer;
    private final Map<FeatureLayer, String> layerToUri;
    private final Map<FeatureLayer, DeepMapItemQuery> childQueries;
    private final Map<FeatureLayer, MapOverlay> childOverlays;

    private ListImpl listModel;

    public WFSManager(MapView mapView) {
        this.mapView = mapView;
        this.context = mapView.getContext();

        this.uriToMime = new IdentityHashMap<>();
        this.uriToLayer = new TreeMap<>();
        this.layerToUri = new IdentityHashMap<>();
        this.childQueries = new IdentityHashMap<>();
        this.childOverlays = new IdentityHashMap<>();
    }

    public synchronized int size() {
        return this.layerToUri.size();
    }

    public synchronized boolean contains(String uri) {
        return this.uriToLayer.containsKey(uri);
    }

    public synchronized boolean add(String uri, String mime,
            FeatureLayer layer) {
        if (this.uriToLayer.containsKey(uri)) {
            Log.w(TAG, "Layer for " + uri + " has already been added.");
            return false;
        } else if (this.layerToUri.containsKey(layer)) {
            Log.w(TAG,
                    "Layer has already been added for "
                            + this.layerToUri.get(layer));
            return false;
        }

        this.uriToMime.put(uri, mime);
        this.uriToLayer.put(uri, layer);
        this.layerToUri.put(layer, uri);

        layer.setVisible(true);
        MapOverlay overlay = new FeatureDataStoreMapOverlay(this.context,
                layer, null);

        this.childOverlays.put(layer, overlay);
        final DeepMapItemQuery childQuery = overlay.getQueryFunction();
        if (childQuery != null)
            this.childQueries.put(layer, childQuery);

        this.mapView.addLayer(MapView.RenderStack.VECTOR_OVERLAYS, layer);

        return true;
    }

    public synchronized void remove(FeatureLayer layer) {
        if (this.layerToUri.containsKey(layer))
            return;

        this.removeImpl(layer);
    }

    public synchronized FeatureLayer remove(String uri) {
        final FeatureLayer retval = this.uriToLayer.get(uri);
        if (retval != null)
            this.removeImpl(retval);
        return retval;
    }

    private void removeImpl(FeatureLayer layer) {
        this.mapView.removeLayer(MapView.RenderStack.VECTOR_OVERLAYS, layer);

        this.childQueries.remove(layer);
        this.childOverlays.remove(layer);

        final String uri = this.layerToUri.remove(layer);
        this.uriToLayer.remove(uri);
        this.uriToMime.remove(uri);
    }

    private synchronized Map<String, FeatureLayer> getUriMap() {
        return new HashMap<>(this.uriToLayer);
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
        for (DeepMapItemQuery query : this.childQueries.values()) {
            retval = query.deepFindItem(metadata);
            if (retval != null)
                break;
        }
        return retval;
    }

    @Override
    public List<MapItem> deepFindItems(Map<String, String> metadata) {
        List<MapItem> retval = new LinkedList<>();
        for (DeepMapItemQuery query : this.childQueries.values())
            retval.addAll(query.deepFindItems(metadata));
        return retval;
    }

    @Override
    public MapItem deepFindClosestItem(GeoPoint location, double threshold,
            Map<String, String> metadata) {

        MapItem retval = null;
        for (DeepMapItemQuery query : this.childQueries.values()) {
            retval = query.deepFindClosestItem(location, threshold, metadata);
            if (retval != null)
                break;
        }
        return retval;
    }

    @Override
    public Collection<MapItem> deepFindItems(GeoPoint location, double radius,
            Map<String, String> metadata) {
        List<MapItem> retval = new LinkedList<>();
        for (DeepMapItemQuery query : this.childQueries.values())
            retval.addAll(query.deepFindItems(location, radius, metadata));
        return retval;
    }

    @Override
    public Collection<MapItem> deepFindItems(GeoBounds bounds,
            Map<String, String> metadata) {
        List<MapItem> retval = new LinkedList<>();
        for (DeepMapItemQuery query : this.childQueries.values())
            retval.addAll(query.deepFindItems(bounds, metadata));
        return retval;
    }

    @Override
    public MapItem deepHitTest(int xpos, int ypos, GeoPoint point,
            MapView view) {
        MapItem retval = null;
        for (DeepMapItemQuery query : this.childQueries.values()) {
            retval = query.deepHitTest(xpos, ypos, point, view);
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
        for (DeepMapItemQuery query : this.childQueries.values()) {
            final SortedSet<MapItem> results = query.deepHitTestItems(xpos,
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
            Map<String, FeatureLayer> uriMap = getUriMap();
            Map<String, SourceListItem> itemMap;
            List<String> removed;
            synchronized (this.itemMap) {
                itemMap = new HashMap<>(this.itemMap);
                removed = new ArrayList<>(this.itemMap.keySet());
            }

            // Create/refresh items
            for (Map.Entry<String, FeatureLayer> entry : uriMap.entrySet()) {
                SourceListItem src = itemMap.get(entry.getKey());
                if (src == null) {
                    src = new SourceListItem(entry.getValue(), entry.getKey(),
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

        public SourceListItem(FeatureLayer layer, String uri, Context context,
                BaseAdapter listener, HierarchyListFilter filter) {

            super(layer, null, context, listener, filter);

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
}
