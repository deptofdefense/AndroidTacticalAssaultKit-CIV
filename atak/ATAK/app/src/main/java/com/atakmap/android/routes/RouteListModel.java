
package com.atakmap.android.routes;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import android.content.Context;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Spinner;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Collections;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.gui.drawable.VisibilityDrawable;
import com.atakmap.android.hierarchy.HierarchyListStateListener;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapComponent;

import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.ItemClick;
import com.atakmap.android.hierarchy.items.MapItemHierarchyListItem;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.routes.nav.NavigationCue;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.user.FilterMapOverlay;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Route manager overlay view
 */
public class RouteListModel extends FilterMapOverlay.ListModelImpl
        implements View.OnClickListener, HierarchyListStateListener {

    private static final String TAG = "RouteListModel";

    private final SharedPreferences _prefs;
    private final Map<Route, RouteListItem> _items = new HashMap<>();
    private View _header;

    // The UID of the route which has its actions view visible
    private static String _actionsUid;

    public RouteListModel(FilterMapOverlay overlay, BaseAdapter listener,
            HierarchyListFilter filter) {
        super(overlay, listener, filter);
        _prefs = PreferenceManager.getDefaultSharedPreferences(
                _mapView.getContext());
    }

    @Override
    public View getHeaderView() {
        if (_header == null) {
            _header = LayoutInflater.from(_mapView.getContext()).inflate(
                    R.layout.route_manager_header, _mapView, false);
            _header.findViewById(R.id.create_route_button)
                    .setOnClickListener(this);
            _header.findViewById(R.id.import_route_button)
                    .setOnClickListener(this);
        }
        updateActionBarState();
        return _header;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.create_route_button) {// Create new route and start editing it
            new RouteCreationDialog(_mapView).show();
        } else if (i == R.id.import_route_button) {
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(RouteMapReceiver.ROUTE_IMPORT));
        }
    }

    @Override
    public boolean onBackButton(HierarchyListAdapter adapter,
            boolean deviceBack) {
        if (deviceBack && !FileSystemUtils.isEmpty(_actionsUid)) {
            // Toggle off selected route when hitting back button
            toggleActions(_actionsUid);
            return true;
        }
        return false;
    }

    @Override
    protected void refreshImpl() {
        //add all child overlays, and sort
        List<HierarchyListItem> filtered = new ArrayList<>();
        List<MapItem> items = _overlay.getItems();
        Map<Route, RouteListItem> itemMap;
        synchronized (_items) {
            itemMap = new HashMap<>(_items);
        }
        boolean actionsValid = false;
        for (MapItem mi : items) {
            if (!(mi instanceof Route))
                continue;
            Route route = (Route) mi;
            RouteListItem item = itemMap.get(route);
            if (item == null) {
                item = new RouteListItem(route);
                itemMap.put(route, item);
            }
            if (!actionsValid && _actionsUid != null
                    && FileSystemUtils.isEquals(_actionsUid, route.getUID()))
                actionsValid = true;
            if (this.filter != null && this.filter.accept(item))
                filtered.add(item);
        }
        synchronized (_items) {
            _items.clear();
            _items.putAll(itemMap);
        }
        if (_actionsUid != null && !actionsValid) {
            // Un-dim the routes if the selected route has been removed
            _actionsUid = null;
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    dimRoutes();
                }
            });
        }
        sortItems(filtered);
        updateChildren(filtered);
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                updateActionBarState();
            }
        });
    }

    @Override
    public void dispose() {
        _actionsUid = null;
        dimRoutes();
        updateActionBarState();
    }

    @Override
    public Set<HierarchyListItem> find(String terms) {
        terms = terms.toLowerCase(LocaleUtil.getCurrent());

        Set<HierarchyListItem> retval = new HashSet<>();
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            if (!(item instanceof RouteListItem))
                continue;
            Route r = ((RouteListItem) item)._route;
            if (match(terms, r.getTitle()))
                // Matched route title
                retval.add(item);
            else {
                // Match checkpoint titles and cues
                PointMapItem[] markers = r.getContactPoints();
                Map<String, NavigationCue> cues = r.getNavigationCues();
                for (PointMapItem pmi : markers) {
                    if (pmi == null)
                        continue;
                    // Checkpoint title match
                    if (match(terms, pmi.getMetaString("callsign", null))) {
                        retval.add(item);
                        break;
                    }
                    // Navigation cue match
                    NavigationCue cue;
                    if ((cue = cues.get(pmi.getUID())) != null
                            && match(terms, cue.getTextCue())) {
                        retval.add(item);
                        break;
                    }
                }
            }
        }
        return retval;
    }

    private boolean match(String terms, String txt) {
        return !FileSystemUtils.isEmpty(txt) && txt.toLowerCase(
                LocaleUtil.getCurrent()).contains(terms);
    }

    /**
     * Dim other routes, if a route is selected.
     */
    private void dimRoutes() {
        RouteMapReceiver.getInstance().dimRoutes(_actionsUid,
                _actionsUid != null, false);
    }

    private HierarchyListAdapter getListener() {
        return this.listener instanceof HierarchyListAdapter
                ? (HierarchyListAdapter) this.listener
                : null;
    }

    private void notifyDataSetChanged() {
        HierarchyListAdapter om = getListener();
        if (om != null)
            om.notifyDataSetChanged();
    }

    private boolean toggleActions(String uid) {
        if (!FileSystemUtils.isEquals(_actionsUid, uid))
            _actionsUid = uid;
        else
            _actionsUid = null;
        notifyDataSetChanged();
        dimRoutes();
        return !FileSystemUtils.isEmpty(_actionsUid);
    }

    private class RouteListItem extends MapItemHierarchyListItem
            implements View.OnClickListener, ItemClick,
            CompoundButton.OnCheckedChangeListener {

        private final Route _route;

        RouteListItem(Route route) {
            super(_mapView, route);
            _route = route;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            if (!super.equals(o))
                return false;
            RouteListItem that = (RouteListItem) o;
            return Objects.equals(_route, that._route);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), _route);
        }

        @Override
        public String getTitle() {
            return _route.getTitle();
        }

        @Override
        public String getDescription() {
            PointMapItem pmi = null;
            final int nPts = _route.getNumPoints();

            if (nPts > 0) {
                if (_route.isReversedDir())
                    pmi = _route.getPointMapItem(nPts - 1);
                else
                    pmi = _route.getPointMapItem(0);
            }
            if (pmi != null && pmi.getPoint() != null) {
                CoordinateFormat cf = CoordinateFormat.find(_prefs.getString(
                        "coord_display_pref", mapView.getContext().getString(
                                R.string.coord_display_pref_default)));
                return CoordinateFormatUtilities.formatToString(
                        pmi.getPoint(), cf);
            }
            return null;
        }

        @Override
        public Object getUserObject() {
            return _route;
        }

        @Override
        public View getListItemView(View v, ViewGroup parent) {
            ViewHolder h = v != null && v.getTag() instanceof ViewHolder
                    ? (ViewHolder) v.getTag()
                    : null;
            if (h == null) {
                h = new ViewHolder();
                v = LayoutInflater.from(mapView.getContext()).inflate(
                        R.layout.route_manager_list_item, parent, false);
                h.icon = v.findViewById(
                        R.id.hierarchy_manager_list_item_icon);
                h.checkBox = v.findViewById(
                        R.id.hierarchy_manager_list_item_checkbox);
                h.viz = v.findViewById(
                        R.id.hierarchy_manager_visibility_iv);
                h.viz.setImageDrawable(h.vizIcon = new VisibilityDrawable());
                h.viz.setTag(h);
                h.title = v.findViewById(
                        R.id.hierarchy_manager_list_item_title);
                h.desc = v.findViewById(
                        R.id.hierarchy_manager_list_item_desc);
                h.actions = v.findViewById(
                        R.id.route_actions_toggle);
                h.actionsLayout = v.findViewById(R.id.route_actions);
                h.details = v.findViewById(
                        R.id.route_manager_details_button);
                h.send = v.findViewById(
                        R.id.route_manager_send_button);
                h.nav = v.findViewById(
                        R.id.route_manager_nav_button);
                h.edit = v.findViewById(
                        R.id.route_manager_edit_button);
                h.delete = v.findViewById(
                        R.id.route_manager_del_button);
                h.reverse = v.findViewById(R.id.reverse_route);
                v.setTag(h);
            }
            h.icon.setImageResource(R.drawable.ic_route);

            h.viz.setOnClickListener(this);
            h.actions.setOnClickListener(this);
            h.details.setOnClickListener(this);
            h.send.setOnClickListener(this);
            h.nav.setOnClickListener(this);
            h.edit.setOnClickListener(this);
            h.delete.setOnClickListener(this);
            h.reverse.setOnClickListener(this);

            HierarchyListAdapter om = getListener();
            boolean selectHandler = om != null && om.getSelectHandler() != null;
            boolean showActions = !selectHandler && FileSystemUtils.isEquals(
                    _actionsUid, getUID());
            h.title.setText(getTitle());
            h.desc.setText(getDescription());
            h.icon.setColorFilter(getIconColor(), PorterDuff.Mode.MULTIPLY);
            if (selectHandler) {
                h.viz.setVisibility(View.GONE);
                h.checkBox.setVisibility(View.VISIBLE);
                h.checkBox.setOnCheckedChangeListener(null);
                h.checkBox.setChecked(om.isChecked(this));
                h.checkBox.setOnCheckedChangeListener(this);
                h.actions.setVisibility(View.GONE);
            } else {
                h.checkBox.setVisibility(View.GONE);
                h.viz.setVisibility(View.VISIBLE);
                h.vizIcon.setVisible(isVisible());
                h.actions.setVisibility(View.VISIBLE);
            }
            h.actionsLayout.setVisibility(showActions ? View.VISIBLE
                    : View.GONE);
            h.actions.setImageResource(showActions ? R.drawable.arrow_down
                    : R.drawable.arrow_right);
            h.delete.setEnabled(_route.getMetaBoolean("removable", true));
            v.setBackgroundColor(om != null && FileSystemUtils.isEquals(
                    om.getHighlightUID(), getUID()) ? 0x8000AF4F : 0);
            return v;
        }

        @Override
        public boolean onClick() {
            if (toggleActions(getUID())) {
                if (!_route.getVisible()) {
                    HierarchyListAdapter om = getListener();
                    if (om != null)
                        om.setVisibleAsync(this, true);
                    else
                        _route.setVisible(true);
                }
                goTo(false);
            }
            return true;
        }

        @Override
        public boolean onLongClick() {
            return false;
        }

        @Override
        public void onClick(View v) {
            List<Intent> intents = new ArrayList<>();
            int i1 = v.getId();

            // Toggle route visibility
            if (i1 == R.id.hierarchy_manager_visibility_iv) {
                boolean viz = !isVisible();
                HierarchyListAdapter om = getListener();
                ViewHolder h = (ViewHolder) v.getTag();
                if (h != null && om != null) {
                    h.vizIcon.setVisible(viz);
                    om.setVisibleAsync(this, viz);
                } else {
                    setVisible(viz);
                    notifyDataSetChanged();
                }
            } else if (i1 == R.id.route_actions_toggle) {// Toggle route actions view
                toggleActions(getUID());

            } else if (i1 == R.id.route_manager_details_button) {// Show route details
                intents.add(new Intent(RouteMapReceiver.SHOW_ACTION)
                        .putExtra("routeUID", getUID()));

            } else if (i1 == R.id.route_manager_send_button) {// Share this route
                intents.add(new Intent(
                        "com.atakmap.android.maps.ROUTE_SHARE")
                                .putExtra("routeUID", getUID()));

            } else if (i1 == R.id.route_manager_nav_button) {// Start route navigation
                // Check to see if the route has a custom nav action sent,
                // otherwise, use the default nav tool.
                String navAction = _route.getMetaString("navAction",
                        RouteMapReceiver.START_NAV);
                Intent[] navIntents = new Intent[] {
                        new Intent("com.atakmap.android.maps.HIDE_COORDS"),
                        new Intent(MapMenuReceiver.HIDE_MENU),
                        new Intent(NavView.TOGGLE_BUTTONS)
                                .putExtra("show", true),
                        new Intent(navAction)
                                .putExtra("routeUID", getUID())
                };

                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(HierarchyListReceiver.CLOSE_HIERARCHY)
                                .putExtra("closeIntents", navIntents));
                return;

            } else if (i1 == R.id.route_manager_edit_button) {// Edit route
                intents.add(new Intent("com.atakmap.android.maps.UNFOCUS"));
                intents.add(new Intent(
                        "com.atakmap.android.maps.HIDE_DETAILS"));
                intents.add(new Intent(MapMenuReceiver.HIDE_MENU));
                // Check to see if the route has a custom edit action sent,
                // otherwise, use the default edit tool.
                String editAction = _route.getMetaString("editAction", "");
                if (editAction.equals("")) {
                    intents.add(new Intent(
                            ToolManagerBroadcastReceiver.BEGIN_TOOL)
                                    .putExtra("tool",
                                            RouteEditTool.TOOL_IDENTIFIER)
                                    .putExtra("routeUID", getUID())
                                    .putExtra("uid", getUID()));
                } else {
                    intents.add(new Intent(editAction)
                            .putExtra("routeUID", getUID())
                            .putExtra("uid", getUID()));
                }

            } else if (i1 == R.id.reverse_route) {
                promptReverseRoute(getUID(), true);

            } else if (i1 == R.id.route_manager_del_button) {// Delete route
                intents.add(new Intent("com.atakmap.android.maps.REMOVE")
                        .putExtra("uid", getUID()));
                intents.add(new Intent("com.atakmap.android.maps.UNFOCUS"));
                intents.add(new Intent(
                        "com.atakmap.android.maps.HIDE_DETAILS"));
                intents.add(new Intent(MapMenuReceiver.HIDE_MENU));

            }
            for (Intent i : intents)
                AtakBroadcast.getInstance().sendBroadcast(i);
        }

        @Override
        public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
            HierarchyListAdapter om = getListener();
            int i = cb.getId();
            if (i == R.id.hierarchy_manager_list_item_checkbox) {
                if (om != null && om.getSelectHandler() != null)
                    om.setItemChecked(this, isChecked);

            }
        }
    }

    private static class ViewHolder {
        TextView title, desc;
        ImageView icon, viz;
        VisibilityDrawable vizIcon;
        CheckBox checkBox;
        View actionsLayout;
        ImageButton actions, details, send, nav, edit, delete, reverse;
    }

    private void promptReverseRoute(final String existingUID,
            final boolean checkIfExists) {
        final Context _context = _mapView.getContext();
        final RouteMapReceiver _receiver = RouteMapReceiver.getInstance();
        final Route _route = _receiver.getRoute(existingUID);

        final String newUID;
        if (existingUID.endsWith(".reversed"))
            newUID = existingUID.replaceAll("\\.reversed", "");
        else
            newUID = existingUID + ".reversed";

        final Route existing = _receiver.getRoute(newUID);
        if (existing != null && checkIfExists) {
            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.route_already_exists);
            b.setMessage(R.string.route_reverse_already_exists);
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            _receiver.getRouteGroup().removeItem(existing);
                            promptReverseRoute(existingUID, false);
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
            return;
        }

        // Lookup planners
        // Get the RouteMapComponent handle
        MapComponent mc = ((MapActivity) _mapView.getContext())
                .getMapComponent(RouteMapComponent.class);
        RoutePlannerManager _routeManager = mc != null
                ? ((RouteMapComponent) mc)
                        .getRoutePlannerManager()
                : null;

        final List<Entry<String, RoutePlannerInterface>> planners = new ArrayList<>();
        if (_routeManager != null) {
            final boolean network = RouteMapReceiver.isNetworkAvailable();
            for (Entry<String, RoutePlannerInterface> k : _routeManager
                    .getRoutePlanners()) {
                if (!k.getValue().isNetworkRequired() || network) {
                    planners.add(k);
                }
            }
        }
        if (FileSystemUtils.isEmpty(planners)) {
            // Confirm if user wants to mirror the route instead
            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.route_reverse);
            String msg;
            if (FileSystemUtils.isEmpty(planners))
                msg = _context.getString(R.string.route_reverse_no_planners);
            else
                msg = _context.getString(R.string.route_reverse_no_network);
            msg += "\n"
                    + _context.getString(R.string.route_reverse_mirror_image);
            b.setMessage(msg);
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            _receiver.mirrorImage(_route, newUID);
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
            return;
        }

        View v = LayoutInflater.from(_context).inflate(
                R.layout.route_reverse_dialog, _mapView, false);
        TextView msg = v.findViewById(R.id.route_reverse_msg);
        msg.setText(_context.getString(R.string.route_reverse_msg,
                _route.getTitle()));
        final CheckBox includeCP = v.findViewById(
                R.id.route_reverse_include_cp);
        final Spinner planSpinner = v.findViewById(
                R.id.route_plan_method);
        final LinearLayout _routePlanOptions = v
                .findViewById(R.id.route_plan_options);

        List<String> plannerNames = new ArrayList<>();
        for (Entry<String, RoutePlannerInterface> k : planners)
            plannerNames.add(k.getValue().getDescriptiveName());
        Collections.sort(plannerNames, RoutePlannerView.ALPHA_SORT);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(_context,
                R.layout.spinner_text_view_dark, plannerNames);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        planSpinner.setAdapter(adapter);

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.route_reverse);
        b.setView(v);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Find selected route planner
                String plannerName = (String) planSpinner
                        .getSelectedItem();
                RoutePlannerInterface rpi = null;
                for (Entry<String, RoutePlannerInterface> k : planners) {
                    if (plannerName.equals(k.getValue()
                            .getDescriptiveName())) {
                        rpi = k.getValue();
                        break;
                    }
                }
                if (rpi == null) {
                    // Should never happen, but just in case
                    Toast.makeText(_context,
                            R.string.route_plan_unknown_host,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if (_route.getNumPoints() < 2)
                    return;

                _receiver.createReversedRoute(_route, newUID, rpi,
                        includeCP.isChecked(), true);
            }
        });
        b.setNeutralButton("Mirror Image",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        _receiver.mirrorImage(_route, newUID);
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        AlertDialog d = b.show();
        RouteCreationDialog.setupPlanSpinner(planSpinner, planners,
                _routePlanOptions, d);
    }

    private boolean _routesVisible = false;

    @Override
    public String getAssociationKey() {
        return "routePreference";
    }

    public void updateActionBarState() {
        String key = DropDownManager.getInstance().getTopDropDownKey();
        boolean visible = key != null && key.equals("routePreference");
        if (_routesVisible != visible) {
            _routesVisible = visible;
        }
    }

    /**
     * Get the intent used to show the Routes list under Overlay Manager
     * @return Routes intent
     */
    public static Intent getRoutesIntent() {
        ArrayList<String> overlayPaths = new ArrayList<>();
        overlayPaths.add("Navigation");
        overlayPaths.add("Routes");
        Intent navTo = new Intent(HierarchyListReceiver.MANAGE_HIERARCHY);
        navTo.putStringArrayListExtra("list_item_paths", overlayPaths);
        navTo.putExtra("isRootList", true);
        return navTo;
    }
}
