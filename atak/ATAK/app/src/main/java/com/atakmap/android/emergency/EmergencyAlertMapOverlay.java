
package com.atakmap.android.emergency;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;

import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.android.emergency.EmergencyAlertReceiver.EmergencyAlert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds Emergency alerts to Overlay Manager
 */
class EmergencyAlertMapOverlay extends AbstractMapOverlay2 {

    private static final String TAG = "EmergencyAlertMapOverlay";

    final private static Set<String> SEARCH_FIELDS = new HashSet<>();
    private final static Set<Class<? extends Action>> ACTION_FILTER = new HashSet<>();
    static {
        ACTION_FILTER.add(GoTo.class);
        ACTION_FILTER.add(Search.class);

        SEARCH_FIELDS.add("callsign");
        SEARCH_FIELDS.add("title");
        SEARCH_FIELDS.add("shapeName");
    }

    private final Context _context;
    private final EmergencyAlertReceiver _receiver;

    EmergencyAlertMapOverlay(MapView mapView, EmergencyAlertReceiver recvr) {
        this._context = mapView.getContext();
        this._receiver = recvr;
    }

    @Override
    public String getIdentifier() {
        return "EmergencyAlertMapOverlay";
    }

    @Override
    public String getName() {
        return _context.getString(R.string.emergency);
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

        return new EmergencyAlertOverlayListModel(adapter, prefFilter);
    }

    private class EmergencyAlertOverlayListModel extends
            AbstractHierarchyListItem2
            implements Search, EmergencyAlertReceiver.OnAlertChangedListener {

        private final String path;

        EmergencyAlertOverlayListModel(BaseAdapter listener,
                HierarchyListFilter filter) {
            this.path = "\\" + _context.getString(R.string.alerts)
                    + "\\" + getUID();
            _receiver.addOnAlertChangedListener(this);
            this.asyncRefresh = true;
            refresh(listener, filter);
        }

        @Override
        public String getTitle() {
            return getName();
        }

        @Override
        public String getIconUri() {
            return "asset://icons/emergency.png";
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public HierarchyListItem getChildAt(int index) {
            List<HierarchyListItem> children = getChildren();
            if (index < 0 || index >= children.size()) {
                Log.w(TAG, "Unable to find alert at index: " + index);
                return null;
            }

            EmergencyAlertListItem item = (EmergencyAlertListItem) children
                    .get(index);
            if (item == null || item._alert == null
                    || !item._alert.isValid()) {
                Log.w(TAG, "Skipping invalid alert at index: " + index);
                return null;
            }

            return item;
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
        public void refreshImpl() {
            // Filter
            List<HierarchyListItem> filtered = new ArrayList<>();
            for (EmergencyAlert alert : EmergencyAlertMapOverlay.this._receiver
                    .getAlerts()) {
                if (alert != null && alert.isValid()) {
                    EmergencyAlertListItem item = new EmergencyAlertListItem(
                            alert);
                    if (this.filter.accept(item))
                        filtered.add(item);
                }
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

        @Override
        public void dispose() {
            super.dispose();
            _receiver.removeOnAlertChangedListener(this);

            // Band-aid fix for issue where tapping the Alerts widget sometimes
            // brings up a blank list - jump to parent list for now
            if (this.listener instanceof HierarchyListAdapter) {
                HierarchyListAdapter om = (HierarchyListAdapter) this.listener;
                if (om.getCurrentList(true) == this)
                    om.popList();
            }
        }

        @Override
        public void onAlertAdded(EmergencyAlert alert) {
            requestRefresh(this.path);
        }

        @Override
        public void onAlertRemoved(EmergencyAlert alert) {
            requestRefresh(this.path);
        }

        /**********************************************************************/
        // Search

        @Override
        public Set<HierarchyListItem> find(String terms) {
            terms = "*" + terms + "*";

            Set<Long> found = new HashSet<>();
            Set<HierarchyListItem> retval = new HashSet<>();
            List<HierarchyListItem> children = getChildren();

            if (children.isEmpty()) {
                Log.d(TAG, "No alerts to search");
                return retval;
            }

            for (String field : SEARCH_FIELDS) {
                for (HierarchyListItem item : children) {
                    EmergencyAlertListItem alertItem = (EmergencyAlertListItem) item;
                    if (item == null || alertItem._alert == null
                            || !alertItem._alert.isValid()) {
                        Log.w(TAG, "Skipping invalid alert");
                        continue;
                    }

                    EmergencyAlert alert = alertItem._alert;
                    if (alert.getItem() != null
                            && !found.contains(alert.getItem()
                                    .getSerialId())) {
                        if (MapGroup.matchItemWithMetaString(
                                alert.getItem(), field, terms)) {
                            retval.add(item);
                            found.add(alert.getItem().getSerialId());
                        }
                    }
                }
            }

            return retval;
        }

        @Override
        public boolean isMultiSelectSupported() {
            return false;
        }
    }

    /**
     * HierarchyListItem for map items which are being tracked
     * Partially based on MapItemHierarchyListItem
     */
    private class EmergencyAlertListItem extends AbstractChildlessListItem
            implements GoTo, ILocation, View.OnClickListener {

        private static final String TAG = "EmergencyAlertListItem";
        private final EmergencyAlert _alert;

        EmergencyAlertListItem(EmergencyAlert alert) {
            this._alert = alert;
        }

        @Override
        public boolean goTo(boolean select) {
            zoomItem(select);
            return true;
        }

        private void zoomItem(boolean menu) {
            if (this._alert == null || !this._alert.isValid()) {
                Log.w(TAG, "Skipping invalid alert zoom");
                return;
            }

            //zoom
            this._alert.onClick();

            //display menus
            String zoomTo = this._alert.getEventUid();
            if (this._alert.getItem() != null) {
                //use current point if possible, to match _alert.onClick
                zoomTo = this._alert.getItem().getUID();
            }

            if (!FileSystemUtils.isEmpty(zoomTo)) {

                Intent localMenu = new Intent();
                Intent localDetails = new Intent();
                if (menu) {
                    localMenu.setAction("com.atakmap.android.maps.SHOW_MENU");
                    localMenu.putExtra("uid", zoomTo);

                    localDetails
                            .setAction("com.atakmap.android.maps.SHOW_DETAILS");
                    localDetails.putExtra("uid", zoomTo);
                }

                ArrayList<Intent> intents = new ArrayList<>(3);
                intents.add(localMenu);
                intents.add(localDetails);

                // broadcast intent
                AtakBroadcast.getInstance().sendIntents(intents);
            }
        }

        @Override
        public String getTitle() {
            if (this._alert == null || !this._alert.isValid()) {
                Log.w(TAG, "Skipping invalid alert title");
                return _context.getString(R.string.emergency);
            }

            return this._alert.getMessage();
        }

        @Override
        public String getIconUri() {
            return "asset://icons/emergency.png";
        }

        @Override
        public Object getUserObject() {
            return this._alert;
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
                v.setTag(h);
            }
            h.delete.setOnClickListener(this);
            return v;
        }

        @Override
        public void onClick(View v) {
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    EmergencyAlertReceiver.REMOVE_ALERT)
                            .putExtra("uid", getUID()));
        }

        @Override
        public String getUID() {
            if (this._alert == null || !this._alert.isValid()) {
                Log.w(TAG, "Skipping invalid alert UID");
                return null;
            }

            return this._alert.getEventUid();
        }

        @Override
        public GeoPoint getPoint(GeoPoint point) {
            if (point != null && point.isMutable()) {
                point.set(_alert.getPoint());
                return point;
            }
            return _alert.getPoint();
        }

        @Override
        public GeoBounds getBounds(MutableGeoBounds bounds) {
            GeoPoint p = getPoint(null);
            double lat = p.getLatitude();
            double lng = p.getLongitude();
            if (bounds != null) {
                bounds.set(lat, lng, lat, lng);
                return bounds;
            }
            return new GeoBounds(lat, lng, lat, lng);
        }
    }

    private static class ExtraHolder {
        ImageButton delete;
    }
}
