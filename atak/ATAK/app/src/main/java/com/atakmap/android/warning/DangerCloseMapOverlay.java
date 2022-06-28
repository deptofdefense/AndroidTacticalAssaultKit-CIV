
package com.atakmap.android.warning;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds Danger Close alerts to Overlay Manager
 */
class DangerCloseMapOverlay extends AbstractMapOverlay2 {

    private static final String TAG = "DangerCloseMapOverlay";

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
    private final SharedPreferences _prefs;
    private final DangerCloseCalculator _calculator;

    DangerCloseMapOverlay(MapView mapView,
            DangerCloseCalculator calculator) {
        _mapView = mapView;
        _context = mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _calculator = calculator;
    }

    @Override
    public String getIdentifier() {
        return TAG;
    }

    @Override
    public String getName() {
        return _context.getString(R.string.danger_close);
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

        return new DangerCloseOverlayListModel(adapter, prefFilter);
    }

    private class DangerCloseOverlayListModel extends
            AbstractHierarchyListItem2
            implements Search {

        DangerCloseOverlayListModel(BaseAdapter listener,
                HierarchyListFilter filter) {
            this.asyncRefresh = true;
            refresh(listener, filter);
        }

        @Override
        public String getTitle() {
            return DangerCloseMapOverlay.this.getName();
        }

        @Override
        public String getIconUri() {
            return "asset://icons/blast_rings.png";
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public HierarchyListItem getChildAt(int index) {
            List<HierarchyListItem> children = getChildren();
            if (FileSystemUtils.isEmpty(children) || index < 0
                    || index >= children.size()) {
                Log.w(TAG, "Unable to find alert at index: " + index);
                return null;
            }
            return children.get(index);
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
        public boolean isMultiSelectSupported() {
            return false;
        }

        @Override
        protected void refreshImpl() {
            List<DangerCloseCalculator.DangerCloseAlert> alerts = DangerCloseMapOverlay.this._calculator
                    .getAlerts();

            // Filter
            List<HierarchyListItem> filtered = new ArrayList<>();
            for (DangerCloseCalculator.DangerCloseAlert alert : alerts) {
                if (alert != null && alert.isValid()) {
                    DangerCloseListItem item = new DangerCloseListItem(alert);
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

        /**********************************************************************/
        // Search

        @Override
        public Set<HierarchyListItem> find(String terms) {
            terms = "*" + terms + "*";

            Set<Long> found = new HashSet<>();
            Set<HierarchyListItem> retval = new HashSet<>();

            List<DangerCloseCalculator.DangerCloseAlert> alerts = DangerCloseMapOverlay.this._calculator
                    .getAlerts();
            if (FileSystemUtils.isEmpty(alerts)) {
                Log.d(TAG, "No alerts to search");
                return retval;
            }

            for (String field : SEARCH_FIELDS) {
                for (DangerCloseCalculator.DangerCloseAlert item : alerts) {
                    if (item == null || !item.isValid()) {
                        Log.w(TAG, "Skipping invalid alert");
                        continue;
                    }

                    if (item.getFriendly() != null
                            && !found.contains(item.getFriendly()
                                    .getSerialId())) {
                        if (MapGroup.matchItemWithMetaString(
                                item.getFriendly(), field,
                                terms)) {
                            retval.add(new DangerCloseListItem(item));
                            found.add(item.getFriendly()
                                    .getSerialId());
                        }
                    }

                    if (item.getHostile() != null
                            && !found.contains(item.getHostile()
                                    .getSerialId())) {
                        if (MapGroup.matchItemWithMetaString(item.getHostile(),
                                field,
                                terms)) {
                            retval.add(new DangerCloseListItem(item));
                            found.add(item.getHostile()
                                    .getSerialId());
                        }
                    }
                }
            }

            return retval;
        }
    }

    /**
     * HierarchyListItem for map items which are being tracked, and are danger close to a hostile
     * Partially based on MapItemHierarchyListItem
     */
    private class DangerCloseListItem extends AbstractChildlessListItem
            implements GoTo, MapItemUser {

        private static final String TAG = "DangerCloseListItem";
        private final DangerCloseCalculator.DangerCloseAlert _alert;

        DangerCloseListItem(DangerCloseCalculator.DangerCloseAlert alert) {
            this._alert = alert;
        }

        @Override
        public boolean goTo(boolean select) {
            zoomItem(select);
            return true;
        }

        private void zoomItem(boolean menu) {
            if (this._alert == null || this._alert.getFriendly() == null) {
                Log.w(TAG, "Skipping invalid alert zoom");
                return;
            }

            Intent zoomIntent = new Intent();
            String zoomTo = this._alert.getFriendly().getUID();
            if (zoomTo != null) {
                zoomIntent.setAction("com.atakmap.android.maps.FOCUS");
                zoomIntent.putExtra("uid", zoomTo);
                zoomIntent.putExtra("useTightZoom", true);
            } else {
                zoomIntent.setAction("com.atakmap.android.maps.ZOOM_TO_LAYER");
                zoomTo = _alert.getFriendly().getPoint().toString();
                zoomIntent.putExtra("point", zoomTo);
            }

            Intent localMenu = new Intent();
            Intent localDetails = new Intent();
            if (menu) {
                localMenu.setAction("com.atakmap.android.maps.SHOW_MENU");
                localMenu.putExtra("uid", zoomTo);

                localDetails.setAction("com.atakmap.android.maps.SHOW_DETAILS");
                localDetails.putExtra("uid", zoomTo);
            }

            ArrayList<Intent> intents = new ArrayList<>(3);
            intents.add(zoomIntent);
            intents.add(localMenu);
            intents.add(localDetails);

            // broadcast intent
            AtakBroadcast.getInstance().sendIntents(intents);
        }

        @Override
        public String getTitle() {
            if (this._alert == null || !this._alert.isValid()) {
                Log.w(TAG, "Skipping invalid alert title");
                return _context.getString(R.string.danger_close);
            }

            return ATAKUtilities.getDisplayName(_alert.getFriendly());
        }

        @Override
        public MapItem getMapItem() {
            return this._alert.getFriendly();
        }

        @Override
        public String getIconUri() {
            if (this._alert == null || !this._alert.isValid()) {
                Log.w(TAG, "Skipping invalid alert icon");
                return null;
            }

            return ATAKUtilities.getIconUri(this._alert.getFriendly());
        }

        @Override
        public int getIconColor() {
            if (this._alert == null || !this._alert.isValid()) {
                Log.w(TAG, "Skipping invalid alert color");
                return Color.WHITE;
            }

            return ATAKUtilities.getIconColor(this._alert.getFriendly());
        }

        @Override
        public <T extends Action> T getAction(Class<T> clazz) {
            if (clazz.equals(GoTo.class))
                return clazz.cast(this);

            return null;
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
                v = LayoutInflater.from(_context).inflate(
                        R.layout.dangerclose_list_item, parent, false);
                h.range = v.findViewById(
                        R.id.danger_close_list_item_dist_text);
                h.bearing = v.findViewById(
                        R.id.danger_close_list_item_bearing_text);
                h.hostile = v.findViewById(
                        R.id.danger_close_list_item_dir_text_hostile);
                h.icon = v.findViewById(
                        R.id.danger_close_list_item_dir_image_hostile);
                v.setTag(h);
            }
            int rangeUnits = Integer.parseInt(_prefs.getString(
                    "rab_rng_units_pref", String.valueOf(Span.METRIC)));
            h.range.setText(SpanUtilities.formatType(rangeUnits,
                    _alert.getDistance(), Span.METER));
            h.bearing.setText(_alert.getDirection());
            h.hostile.setText(ATAKUtilities.getDisplayName(
                    _alert.getHostile()));
            ATAKUtilities.SetIcon(_context, h.icon, _alert.getHostile());
            return v;
        }

        @Override
        public String getUID() {
            if (this._alert == null || !this._alert.isValid()) {
                Log.w(TAG, "Skipping invalid alert UID");
                return null;
            }

            return this._alert.getFriendly().getUID();
        }
    }

    private static class ExtraHolder {
        TextView range, bearing, hostile;
        ImageView icon;
    }
}
