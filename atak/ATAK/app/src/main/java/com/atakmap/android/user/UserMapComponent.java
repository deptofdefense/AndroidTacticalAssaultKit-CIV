
package com.atakmap.android.user;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import android.preference.PreferenceManager;

import com.atakmap.android.coordinate.PolarCoordinateReceiver;
import com.atakmap.android.icons.IconsMapAdapter;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapOverlayManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.overlay.MapOverlayParent;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.tools.MovePointTool;
import com.atakmap.android.user.icon.SpotMapReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.app.R;
import com.atakmap.app.preferences.GeocoderPreferenceFragment;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Sets up handlers for a number of core map-based interactions with ATAK, including placing points,
 * removing them, moving them, etc. RESPONDS TO - com.atakmap.android.maps.TASK -
 * 
 * 
 */
public class UserMapComponent extends AbstractMapComponent {

    public static final String TAG = "UserMapComponent";

    public static final String CLAMP_TOGGLE = "com.atakmap.android.maps.CLAMP_TOGGLE";

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected MapView _mapView;
    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected TaskableMarkerListAdapter _taskMarkerList;
    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected MapGroup _userGroup, _spotMapGroup, _usericonGroup, _missionGroup,
            _casevacGroup;
    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected Map<String, MapGroup> _userGroups;
    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected CamLockerReceiver _camLockReceiver;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _mapView = view;

        _mapView.getMapData().putBoolean("disableTasking", true);
        String[] groupNames = {
                "Hostile",
                "Friendly",
                "Neutral",
                "Unknown",
                "Waypoint",
                "Route",
                "Other"
        };
        String[] overlayIds = {
                "hostile",
                "friendly",
                "neutral",
                "unknown",
                "waypoint",
                "route",
                "other"
        };

        _initUserGroups(groupNames, overlayIds);
        PlacePointTool.init(_usericonGroup, _missionGroup, _casevacGroup,
                _userGroups);
        _registerReceivers(context);
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                EnterLocationTool.TOOL_NAME, new EnterLocationTool(view));

        // Initialize map click tool
        new MapClickTool(view);

        Collection<FilterMapOverlay> filterOverlays;
        try {
            //wrap "Markers" top level overlay will contain some XML/FilterMapOverlays and some
            //programatically created ones e.g. "Icons". See MapOverlayManager.addMarkersOverlay
            MapOverlayManager overlayManager = view.getMapOverlayManager();
            MapOverlayParent.getOrAddParent(view, "markerroot",
                    "Markers", "asset://icons/affiliations.png", 2, true);

            filterOverlays = FilterMapOverlay.createDefaultFilterOverlays(view);
            for (FilterMapOverlay overlay : filterOverlays) {
                String parentId = overlay.getParentId();
                if (FileSystemUtils.isEmpty(parentId)) {
                    //Log.d(TAG, "Adding top level FilterMapOverlay: " + overlay.getName());
                    overlayManager.addOverlay(overlay);
                } else {
                    MapOverlay parent = overlayManager.getOverlay(parentId);
                    if (parent instanceof MapOverlayParent) {
                        //Log.d(TAG, "Adding FilterMapOverlay: " + overlay.getName() + " to parent: " + parentId);
                        overlayManager.addOverlay((MapOverlayParent) parent,
                                overlay);
                    } else {
                        Log.w(TAG, "Unable to find parent overlay: " + parentId
                                + ". Adding top level FilterMapOverlay: "
                                + overlay.getName());
                        overlayManager.addOverlay(overlay);
                    }
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "Unexpected IO error creating filter overlays.", e);
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Unexpected XML parser error creating filter overlays.",
                    e);
        }
        GeocoderPreferenceFragment gpf = new GeocoderPreferenceFragment();

        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        context.getString(R.string.geocoderPreference),
                        context.getString(R.string.preferences_text381),
                        "geocoderPreferences",
                        context.getResources().getDrawable(
                                R.drawable.ic_menu_goto),
                        gpf));

    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void _initUserGroups(String[] names, String[] overlays) {
        // TODO: Why are we creating a Hostile group under both "User Objects"
        // TODO: and "Cursor on Target"??? (see CotMapAdapter)
        _userGroup = new DefaultMapGroup("User Objects");
        _userGroup.setMetaBoolean("permaGroup", true);
        _userGroup.setMetaBoolean("addToObjList", false);
        _mapView.getMapOverlayManager().addOtherOverlay(
                new DefaultMapGroupOverlay(_mapView, _userGroup,
                        FilterMapOverlay
                                .getRejectFilter(_mapView)));
        _userGroups = new HashMap<>();
        for (int i = 0; i < names.length; ++i) {
            MapGroup group = new DefaultMapGroup(names[i], overlays[i], true);
            _userGroup.addGroup(group);
            _userGroups.put(names[i], group);
        }

        _spotMapGroup = new DefaultMapGroup("Spot Map", "spotmap", true);
        _mapView.getRootGroup().addGroup(_spotMapGroup);

        IconsMapAdapter.initializeUserIconDB(_mapView.getContext(),
                PreferenceManager
                        .getDefaultSharedPreferences(_mapView.getContext()));

        _usericonGroup = new DefaultMapGroup("Icons", "usericons", true);
        _usericonGroup.setMetaInteger("groupOverlayOrder", 2);
        //attempt to get icon for this map group
        UserIcon.setGroupIcon(_usericonGroup, null, _mapView.getContext());
        _mapView.getMapOverlayManager().addMarkersOverlay(
                new DefaultMapGroupOverlay(_mapView, _usericonGroup));

        _missionGroup = new DefaultMapGroup("Mission", "missionmarkers", false);
        _missionGroup.setMetaInteger("groupOverlayOrder", 3);
        _missionGroup.setMetaString("iconUri",
                "asset://icons/sensor_location.png");
        _mapView.getMapOverlayManager().addMarkersOverlay(
                new DefaultMapGroupOverlay(_mapView, _missionGroup));

        _casevacGroup = new DefaultMapGroup("CASEVAC", "CASEVAC", true);
        _casevacGroup.setMetaString("iconUri",
                "asset://icons/damaged.png");
        _mapView.getMapOverlayManager().addMarkersOverlay(
                new DefaultMapGroupOverlay(_mapView, _casevacGroup));

        MapGroup airspaceGroup = new DefaultMapGroup("Airspace");
        airspaceGroup.setMetaBoolean("permaGroup", true);
        airspaceGroup.setMetaString("iconUri",
                "asset://icons/piicon_initial.png");
        _mapView.getMapOverlayManager().addMarkersOverlay(
                new DefaultMapGroupOverlay(_mapView, airspaceGroup));
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        // Debug.stopMethodTracing();
        // clean up Singleton

        if (_mapView != null && _camLockReceiver != null) {
            _mapView.getMapController()
                    .removeOnPanRequestedListener(_camLockReceiver);
            _camLockReceiver.dispose();
        }
    }

    private void _registerReceivers(Context context) {
        // com.atakmap.android.maps.PLACE
        BroadcastReceiver _placeReceiver = new PlaceBroadcastReceiver(_mapView);
        DocumentedIntentFilter placeFilter = new DocumentedIntentFilter();
        placeFilter.addAction("com.atakmap.android.maps.PLACE");
        this.registerReceiver(context, _placeReceiver, placeFilter);

        // com.atakmap.android.maps.POLAR_COORD_ENTRY
        PolarCoordinateReceiver polarCoordReceiver = new PolarCoordinateReceiver(
                _mapView);
        DocumentedIntentFilter polarFilter = new DocumentedIntentFilter();
        polarFilter.addAction("com.atakmap.android.maps.POLAR_COORD_ENTRY");
        this.registerReceiver(context, polarCoordReceiver, polarFilter);

        // com.atakmap.android.maps.REMOVE
        BroadcastReceiver _removeReceiver = new RemoveBroadcastReceiver(
                _mapView);
        DocumentedIntentFilter removeFilter = new DocumentedIntentFilter();
        removeFilter.addAction("com.atakmap.android.maps.REMOVE");
        this.registerReceiver(context, _removeReceiver, removeFilter);

        // com.atakmap.android.maps.MANUAL_POINT_ENTRY
        BroadcastReceiver _manualPointEntryReceiver = new ManualPointEntryReceiver(
                _mapView);
        DocumentedIntentFilter manualPointEntryFilter = new DocumentedIntentFilter();
        manualPointEntryFilter
                .addAction("com.atakmap.android.maps.MANUAL_POINT_ENTRY");
        this.registerReceiver(context, _manualPointEntryReceiver,
                manualPointEntryFilter);

        // com.atakmap.android.maps.FOCUS
        BroadcastReceiver _focusReceiver = new FocusBroadcastReceiver(_mapView);
        DocumentedIntentFilter focusFilter = new DocumentedIntentFilter();
        focusFilter.addAction("com.atakmap.android.maps.FOCUS");
        focusFilter.addAction("com.atakmap.android.maps.UNFOCUS");
        focusFilter
                .addAction("com.atakmap.android.maps.UNFOCUS_FOR_FINE_ADJUST");
        focusFilter.addAction("com.atakmap.android.maps.FOCUS_UNFOCUS");
        focusFilter.addAction("com.atakmap.android.maps.FOCUS_DISPLAY");
        this.registerReceiver(context, _focusReceiver, focusFilter);

        // com.atakmap.android.maps.TASK
        _taskMarkerList = new TaskableMarkerListAdapter(context);
        BroadcastReceiver _taskReceiver = new TaskBroadcastReceiver(
                _mapView.getMapEventDispatcher(), _taskMarkerList);
        DocumentedIntentFilter taskFilter = new DocumentedIntentFilter();
        taskFilter.addAction("com.atakmap.android.maps.TASK");
        this.registerReceiver(context, _taskReceiver, taskFilter);

        // com.atakmap.android.map.action.LOCK_CAM
        _camLockReceiver = new CamLockerReceiver(_mapView);
        DocumentedIntentFilter camLockFilter = new DocumentedIntentFilter();
        camLockFilter.addAction(CamLockerReceiver.LOCK_CAM);
        camLockFilter.addAction(CamLockerReceiver.UNLOCK_CAM);
        camLockFilter.addAction("com.atakmap.android.maps.SHOW_MENU");
        camLockFilter.addAction("com.atakmap.android.maps.HIDE_MENU");
        camLockFilter.addAction(MapMode.TRACK_UP.getIntent());
        camLockFilter.addAction(MapMode.NORTH_UP.getIntent());
        camLockFilter.addAction(MapMode.MAGNETIC_UP.getIntent());
        camLockFilter.addAction(MapMode.USER_DEFINED_UP.getIntent());
        this.registerReceiver(context, _camLockReceiver, camLockFilter);
        _mapView.getMapController().addOnPanRequestedListener(_camLockReceiver);

        BroadcastReceiver _enterLocationDropDownReceiver = EnterLocationDropDownReceiver
                .getInstance(_mapView);
        DocumentedIntentFilter enterLocationFilter = new DocumentedIntentFilter();
        enterLocationFilter.addAction(EnterLocationDropDownReceiver.START,
                "Open the point dropper drop-down", new DocumentedExtra[] {
                        new DocumentedExtra("iconPallet",
                                "The class of the icon pallet to show",
                                true, Class.class),
                        new DocumentedExtra("select",
                                "The resource ID of the button to select",
                                true, Integer.class),
                        new DocumentedExtra("callback",
                                "Intent to broadcast when a point is dropped",
                                true, Intent.class)
                });
        enterLocationFilter.addAction(IconsMapAdapter.ICONSET_ADDED);
        enterLocationFilter.addAction(IconsMapAdapter.ICONSET_REMOVED);
        enterLocationFilter.addAction("com.atakmap.android.user.GO_TO");
        this.registerReceiver(context, _enterLocationDropDownReceiver,
                enterLocationFilter);

        BroadcastReceiver _recentlyAddedDropDownReceiver = RecentlyAddedDropDownReceiver
                .getInstance();
        DocumentedIntentFilter recentlyAddedFilter = new DocumentedIntentFilter();
        recentlyAddedFilter.addAction(RecentlyAddedDropDownReceiver.START);
        recentlyAddedFilter.addAction("com.atakmap.android.maps.COT_PLACED");
        recentlyAddedFilter
                .addAction("com.atakmap.android.cotdetails.COTINFO_SETTYPE");
        recentlyAddedFilter
                .addAction("com.atakmap.android.maps.COT_RECENTLYPLACED");
        this.registerReceiver(context, _recentlyAddedDropDownReceiver,
                recentlyAddedFilter);

        // Possible to remove - does not look like it is used anymore //
        // com.atakmap.android.action.OPEN_POINT_GUI
        BroadcastReceiver _dropGuiReceiver = new DropItemGuiReceiver(_mapView);
        DocumentedIntentFilter dropGuiFilter = new DocumentedIntentFilter();
        dropGuiFilter
                .addAction("com.atakmap.android.action.OPEN_POINT_GUI");
        this.registerReceiver(context, _dropGuiReceiver, dropGuiFilter);

        BroadcastReceiver spotMapReceiver = new SpotMapReceiver(_mapView,
                _spotMapGroup);
        DocumentedIntentFilter spotMapFilter = new DocumentedIntentFilter();
        spotMapFilter.addAction(SpotMapReceiver.SPOT_DETAILS);
        spotMapFilter.addAction(SpotMapReceiver.PLACE_SPOT);
        spotMapFilter.addAction(SpotMapReceiver.TOGGLE_LABEL);
        this.registerReceiver(context, spotMapReceiver, spotMapFilter);

        DocumentedIntentFilter clampToggle = new DocumentedIntentFilter();
        clampToggle.addAction(CLAMP_TOGGLE,
                "Listen for a generic action that toggles the rendering mode of a line within the system clamp to ground or absolute");
        AtakBroadcast.getInstance().registerReceiver(clampToggleReceiver,
                clampToggle);

        CompassOverlayReceiver cor = new CompassOverlayReceiver(_mapView);
        DocumentedIntentFilter compassFilter = new DocumentedIntentFilter();
        compassFilter.addAction("com.atakmap.maps.DRAW_COMPASS_OVERLAY");
        AtakBroadcast.getInstance().registerReceiver(cor, compassFilter);

        MapGroup.deepMapItems(_mapView.getRootGroup(),
                new MapGroup.OnItemCallback<Marker>(
                        Marker.class) {
                    @Override
                    public boolean onMapItem(Marker item) {
                        _registerIfTaskable(item);
                        return false;
                    }
                });

        // add listener for any later added taskable markers
        _mapView.getMapEventDispatcher()
                .addMapEventListener(MapEvent.ITEM_ADDED, _itemAddedListner);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_LONG_PRESS,
                new MapEventDispatcher.MapEventDispatchListener() {

                    @Override
                    public void onMapEvent(MapEvent event) {

                        MapItem mi = event.getItem();

                        if (mi instanceof DrawingShape) {
                            GeoPoint clickPoint = mi.getClickPoint();
                            mi = ATAKUtilities.findAssocShape(mi);
                            if (mi instanceof MultiPolyline) {
                                // Start point movement tool (MovePointTool)
                                mi.setClickPoint(clickPoint);
                                Intent i = new Intent(
                                        ToolManagerBroadcastReceiver.BEGIN_SUB_TOOL);
                                i.putExtra("tool",
                                        MovePointTool.TOOL_IDENTIFIER);
                                i.putExtra("uid", mi.getUID());
                                AtakBroadcast.getInstance().sendBroadcast(i);
                            }
                            return;
                        } else if (!(mi instanceof PointMapItem))
                            return;

                        PointMapItem item = (PointMapItem) mi;
                        if (item.getMetaString("routeUID", null) != null) {
                            // this is an association marker
                            return;
                        }

                        // TODO do we want to fix this?
                        final String type = item.getType();

                        // Don't allow user to move the point if it's not movable
                        if (type.equals("shape_marker") ||
                                type.equals("crit_marker") ||
                                type.equals("square_marker") ||
                                type.equals("rectangle_marker") ||
                                type.equals("rectangle_width_marker") ||
                                type.equals("rectangle_side_marker") ||
                                !item.getMovable())
                            return;

                        // Start point movement tool (MovePointTool)
                        Intent i = new Intent(
                                ToolManagerBroadcastReceiver.BEGIN_SUB_TOOL);
                        i.putExtra("tool", MovePointTool.TOOL_IDENTIFIER);
                        i.putExtra("uid", item.getUID());
                        AtakBroadcast.getInstance().sendBroadcast(i);
                    }
                });
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void _registerIfTaskable(final Marker marker) {
        if (marker.hasMetaValue("tasks")) {
            _taskMarkerList.addMarker(marker);
            _mapView.getMapData().putBoolean("disableTasking", false);
            marker.addOnGroupChangedListener(
                    new MapItem.OnGroupChangedListener() {

                        @Override
                        public void onItemAdded(MapItem item, MapGroup event) {
                        }

                        @Override
                        public void onItemRemoved(MapItem item,
                                MapGroup event) {
                            if (item.getUID()
                                    .equals(marker.getMetaString("uid",
                                            "missing"))) {
                                Log.e(TAG,
                                        "Trying to remove"
                                                + marker.getMetaString(
                                                        "callsign",
                                                        "n/a"));
                                _taskMarkerList.removeMarker(marker);
                                if (_taskMarkerList.isEmpty())
                                    _mapView.getMapData().putBoolean(
                                            "disableTasking",
                                            true);
                            }
                        }

                    });
        }
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected final MapEventDispatcher.MapEventDispatchListener _itemAddedListner = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            if (event.getItem() instanceof Marker) {
                // text content is 1 over the map scale e.g. 50000 gets displayed at 1:50K
                if (event.getItem().getType().startsWith("a-f-A")) {
                    event.getItem().setMetaDouble("minRenderScale",
                            1.0d / 100000000); // <!-- 1:100M -->

                }

                _registerIfTaskable((Marker) event.getItem());
            }
        }
    };

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected final BroadcastReceiver clampToggleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null && action.equals(CLAMP_TOGGLE)) {
                String uid = intent.getStringExtra("uid");
                String shapeUID = intent.getStringExtra("shapeUID");
                MapItem mi = null;
                if (!FileSystemUtils.isEmpty(shapeUID))
                    mi = _mapView.getMapItem(shapeUID);
                if (mi == null)
                    mi = _mapView.getMapItem(uid);
                if (mi == null)
                    return;
                if (mi.getAltitudeMode() != AltitudeMode.ClampToGround)
                    mi.setAltitudeMode(AltitudeMode.ClampToGround);
                else
                    mi.setAltitudeMode(AltitudeMode.Absolute);
            }
        }
    };

}
