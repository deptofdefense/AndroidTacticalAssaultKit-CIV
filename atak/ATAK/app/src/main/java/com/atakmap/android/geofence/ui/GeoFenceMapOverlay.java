
package com.atakmap.android.geofence.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;

import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.geofence.component.GeoFenceComponent;
import com.atakmap.android.geofence.data.GeoFence;
import com.atakmap.android.geofence.data.GeoFence.MonitoredTypes;
import com.atakmap.android.geofence.data.GeoFenceDatabase;
import com.atakmap.android.geofence.monitor.GeoFenceManager;
import com.atakmap.android.geofence.monitor.GeoFenceMonitor;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds Geo Fences to Overlay Manager
 */
public class GeoFenceMapOverlay extends AbstractMapOverlay2 implements Delete {

    private static final String TAG = "GeoFenceMapOverlay";

    final static Set<String> SEARCH_FIELDS = new HashSet<>();
    private final static Set<Class<? extends Action>> ACTION_FILTER = new HashSet<>();
    static {
        ACTION_FILTER.add(GoTo.class);
        ACTION_FILTER.add(Search.class);

        SEARCH_FIELDS.add("callsign");
        SEARCH_FIELDS.add("title");
        SEARCH_FIELDS.add("shapeName");
    }

    private final MapView _mapView;
    private final Context _context;
    private final GeoFenceDatabase _database;
    private final MapGroup _group;
    private final GeoFenceManager _manager;

    private boolean _purged;
    private GeoFenceOverlayListModel _listModel;

    public GeoFenceMapOverlay(MapView mapView, GeoFenceDatabase database,
            GeoFenceManager manager) {
        this._mapView = mapView;
        this._context = mapView.getContext();
        this._database = database;
        this._group = mapView.getRootGroup();
        this._manager = manager;
        this._purged = false;
    }

    @Override
    public String getIdentifier() {
        return "GeoFenceMapOverlay";
    }

    @Override
    public String getName() {
        return _mapView.getContext().getString(R.string.geo_fences);
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
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, HierarchyListFilter prefFilter) {

        //so here we purge DB first time Overlay Manager runs
        if (!_purged) {
            Log.d(TAG, "Purging Geo Fence DB");
            List<GeoFence> dbFences = _database.getGeoFences(_manager);
            List<GeoFence> fencesToRemove = new ArrayList<>();
            for (GeoFence fence : dbFences) {
                if (fence == null) {
                    Log.w(TAG, "Empty GeoFence in db");
                    continue;
                }

                if (!fence.isValid()) {
                    Log.w(TAG,
                            "Removing invalid GeoFence from db: "
                                    + fence);
                    fencesToRemove.add(fence);
                    continue;
                }

                MapItem item = _group.deepFindUID(fence.getMapItemUid());
                if (item == null) {
                    Log.w(TAG,
                            "Removing missing GeoFence from db: "
                                    + fence);
                    fencesToRemove.add(fence);
                }
            } //end DB fence loop

            if (fencesToRemove.size() > 0) {
                Log.d(TAG, "Removing fence count: " + fencesToRemove.size());
                for (GeoFence toRemove : fencesToRemove) {
                    Log.d(TAG, "Removing fence: " + toRemove.toString());
                    _database.remove(toRemove.getMapItemUid());
                }
            }
            _purged = true;
        }

        if (_listModel == null) {
            _listModel = new GeoFenceOverlayListModel();
            _manager.addMonitorListener(_listModel);
        }
        _listModel.refresh(adapter, prefFilter);
        return _listModel;
    }

    @Override
    public boolean delete() {
        return _listModel != null && _listModel.delete();
    }

    private class GeoFenceOverlayListModel extends AbstractHierarchyListItem2
            implements Search, Delete, GeoFenceComponent.GeoFenceListener,
            GeoFenceManager.MonitorListener, View.OnClickListener {

        private final String path;
        private final Map<GeoFence, GeoFenceListModel> _listMap = new HashMap<>();

        GeoFenceOverlayListModel() {
            this.path = "\\" + _context.getString(R.string.alerts)
                    + "\\" + getUID();
            this.asyncRefresh = true;
            this.reusable = true;
        }

        @Override
        public void onFenceAdded(GeoFence fence, MapItem item) {
            requestRefresh(this.path);
        }

        @Override
        public void onFenceChanged(GeoFence fence, MapItem item) {
            requestRefresh(this.path);
        }

        @Override
        public void onFenceRemoved(String mapItemUid) {
            requestRefresh(this.path);
        }

        @Override
        public void onMonitorAdded(GeoFenceMonitor monitor) {
            requestRefresh(this.path);
        }

        @Override
        public void onMonitorChanged(GeoFenceMonitor monitor) {
            requestRefresh(this.path);
        }

        @Override
        public void onMonitorRemoved(String uid) {
            requestRefresh(this.path);
        }

        @Override
        public String getTitle() {
            return GeoFenceMapOverlay.this.getName();
        }

        @Override
        public String getIconUri() {
            if (_manager.getAlerting().getAlertCount() > 0) {
                return "asset://icons/geofence.png";
            }

            List<GeoFence> fences = _database.getGeoFences(_manager);
            if (FileSystemUtils.isEmpty(fences)) {
                return "asset://icons/geofence_disabled.png";
            }

            for (GeoFence fence : fences) {
                if (_manager.isTracking(fence)) {
                    return "asset://icons/geofence_noalerts.png";
                }
            }

            return "asset://icons/geofence_disabled.png";
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public Object getUserObject() {
            return null;
        }

        @Override
        public View getExtraView(View v, ViewGroup parent) {
            ExtraHolder h = v != null && v.getTag() instanceof ExtraHolder
                    ? (ExtraHolder) v.getTag()
                    : null;
            if (h == null) {
                h = new ExtraHolder();
                v = h.delete = (ImageButton) LayoutInflater.from(_context)
                        .inflate(R.layout.delete_button, parent, false);
                h.delete.setOnClickListener(this);
                v.setTag(h);
            }
            h.delete.setEnabled(getChildCount() >= 1);
            return v;
        }

        @Override
        public void onClick(View v) {
            final AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.confirm_delete);
            b.setMessage(R.string.geofence_dismiss_alerts_or_delete_geofences);
            b.setPositiveButton(R.string.dismiss_all,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            _manager.dismissAll();
                        }
                    });
            b.setNeutralButton(R.string.delete_all,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            _manager.deleteAll();
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(new Intent(
                                            DropDownManager.CLOSE_DROPDOWN));
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }

        @Override
        protected void refreshImpl() {
            List<GeoFence> fences = _database.getGeoFences(_manager);

            // Filter
            Map<GeoFence, GeoFenceListModel> readMap, writeMap;
            writeMap = new HashMap<>();
            synchronized (_listMap) {
                readMap = new HashMap<>(_listMap);
            }
            List<HierarchyListItem> filtered = new ArrayList<>();
            for (GeoFence fence : fences) {
                //Use the shape (not the reference map item)
                MapItem mi = _group.deepFindUID(fence.getMapItemUid());
                if (mi == null) {
                    Log.w(TAG, "Unable to find fence map item: "
                            + fence.getMapItemUid());
                    continue;
                }

                // Skip custom fences with no monitored items
                if (fence.getMonitoredTypes() == MonitoredTypes.Custom) {
                    GeoFenceMonitor m = _manager.getMonitor(
                            fence.getMapItemUid());
                    if (m == null || !m.hasTrackedItems())
                        continue;
                }

                GeoFenceListModel item = readMap.get(fence);
                if (item == null)
                    item = new GeoFenceListModel(_mapView, _manager, fence, mi);
                item.syncRefresh(this.listener, this.filter);
                writeMap.put(fence, item);

                if (this.filter.accept(item))
                    filtered.add(item);
            }

            synchronized (_listMap) {
                _listMap.clear();
                _listMap.putAll(writeMap);
            }

            // Sort
            sortItems(filtered);

            // Update
            updateChildren(filtered);
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        /**********************************************************************/
        // Search

        @Override
        public Set<HierarchyListItem> find(String terms) {
            terms = "*" + terms + "*";

            Set<Long> found = new HashSet<>();
            Set<HierarchyListItem> retval = new HashSet<>();

            List<GeoFence> fences = GeoFenceMapOverlay.this._database
                    .getGeoFences(_manager);
            if (FileSystemUtils.isEmpty(fences)) {
                Log.d(TAG, "No geo fences to search");
                return retval;
            }

            List<MapItem> results;
            List<HierarchyListItem> children = getChildren();
            for (String field : SEARCH_FIELDS) {
                results = _group.deepFindItems(field, terms);
                for (MapItem item : results) {
                    if (found.contains(item.getSerialId()))
                        continue;

                    for (HierarchyListItem list : children) {
                        GeoFenceListModel fence = (GeoFenceListModel) list;
                        if (fence.getMapItem().getUID()
                                .equals(item.getUID())) {
                            retval.add(fence);
                            found.add(item.getSerialId());
                        }
                    }
                }
            }

            //TODO deep search to search for callsign of alerting map items (see GeoFenceListModel)?
            return retval;
        }

        @Override
        public boolean delete() {
            _manager.dismissAll();
            return true;
        }
    }

    private static class ExtraHolder {
        ImageButton delete;
    }
}
